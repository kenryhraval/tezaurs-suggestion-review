package lv.tezaurs.tip.review.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.review.dto.ReviewListItemResponse;
import lv.tezaurs.tip.review.dto.ReviewPageResponse;
import lv.tezaurs.tip.review.dto.ReviewResponse;
import lv.tezaurs.tip.review.dto.ReviewSort;
import lv.tezaurs.tip.review.dto.DuplicateReviewCandidateResponse;
import lv.tezaurs.tip.review.dto.UpdateReviewTitleRequest;
import lv.tezaurs.tip.review.dto.UpdateReviewTypeRequest;
import lv.tezaurs.tip.review.dto.UpdateStatusRequest;
import lv.tezaurs.tip.review.dto.UsageDecisionRequest;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.entry.service.EntryService;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final String PUBLIC_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PUBLIC_ID_LENGTH = 6;

    private final ReviewRepository reviewRepository;
    private final ReviewSuggestionRepository reviewSuggestionRepository;
    private final TezaursEntryRepository tezaursEntryRepository;
    private final CurrentUserService currentUserService;
    private final EntryService entryService;
    private final SenseRepository senseRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Review createFromSuggestion(String submittedTerm, ReviewStatus initialStatus) {
        String publicId = generatePublicId();

        return reviewRepository.save(new Review(submittedTerm, initialStatus, publicId));
    }

    @Transactional(readOnly = true)
    public ReviewPageResponse list(
            ReviewStatus status, List<ReviewType> types, String query, int page, int size, ReviewSort sort) {
        String searchQuery = query == null ? "" : query.trim();
        List<ReviewType> includedTypes = types == null || types.isEmpty()
                ? List.of(ReviewType.values()) : types;

        Sort reviewSort = switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case MOST_SUGGESTIONS -> JpaSort.unsafe(Sort.Direction.DESC, "SIZE(review.suggestions)")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        };
        reviewSort = reviewSort.and(Sort.by("id"));

        return ReviewPageResponse.from(reviewRepository.findForReviewQueue(
                status, includedTypes, searchQuery, PageRequest.of(page, size, reviewSort))
                .map(ReviewListItemResponse::from));
    }

    @Transactional(readOnly = true)
    public ReviewResponse findById(UUID reviewId) {
        return ReviewResponse.from(getReviewOrThrow(reviewId));
    }

    @Transactional(readOnly = true)
    public ReviewResponse findByPublicId(String publicId) {
        Review review = reviewRepository.findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + publicId + "” netika atrasts."));

        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse changeStatus(UUID reviewId, UpdateStatusRequest request, String authorization) {
        String username = currentUserService.requireUsername(authorization);
        Review review = getReviewOrThrow(reviewId);

        applyStatusChange(review, request.status(), username);

        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<DuplicateReviewCandidateResponse> findDuplicateCandidates(String query, UUID excludedReviewId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return reviewRepository.findDuplicateCandidates(
                        excludedReviewId, List.of(ReviewStatus.NEW, ReviewStatus.GARBAGE), query.trim(),
                        PageRequest.of(0, 5, Sort.by("reviewedTerm")))
                .stream()
                .map(review -> DuplicateReviewCandidateResponse.from(
                        review, reviewSuggestionRepository.countByReviewId(review.getId())))
                .toList();
    }

    @Transactional
    public ReviewResponse mergeReviews(UUID sourceReviewId, UUID targetReviewId) {
        if (sourceReviewId.equals(targetReviewId)) {
            throw new ConflictException("Perēkli nevar apvienot ar sevi.");
        }

        Review sourceReview = lockReviewOrThrow(sourceReviewId);
        Review targetReview = lockReviewOrThrow(targetReviewId);

        for (ReviewSuggestion link : List.copyOf(sourceReview.getSuggestions())) {
            link.reassignTo(targetReview);
        }

        // Persist the moved links before deleting the source review with ON DELETE CASCADE.
        reviewSuggestionRepository.flush();
        reviewRepository.delete(sourceReview);

        return ReviewResponse.from(targetReview);
    }

    @Transactional
    public ReviewResponse moveSuggestionToNewReview(UUID reviewId, Integer suggestionId) {
        Review review = lockReviewOrThrow(reviewId);
        ReviewSuggestion link = review.getSuggestions().stream()
                .filter(item -> item.getSourceSuggestion().getId().equals(suggestionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Ieteikums nepieder šim perēklim."));

        if (review.getSuggestions().size() == 1) {
            throw new ConflictException("Perēkļa vienīgo ieteikumu nevar atdalīt.");
        }

        Review newReview = createFromSuggestion(link.getSourceSuggestion().getLemma(), ReviewStatus.NEW);
        link.reassignTo(newReview);

        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse updateTitle(UUID reviewId, UpdateReviewTitleRequest request) {
        Review review = getReviewOrThrow(reviewId);

        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Izmaiņas iespējamas tikai stāvoklī “Apstrādē”.");
        }

        review.setReviewedTerm(request.reviewedTerm().trim());

        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse changeType(UUID reviewId, UpdateReviewTypeRequest request, String authorization) {
        String username = currentUserService.requireUsername(authorization);
        Review review = lockReviewOrThrow(reviewId);

        validateTypeChange(review, request);

        review.setType(request.type());
        review.setMatchedEntryId(request.matchedEntryId());

        if (Boolean.TRUE.equals(request.meaningAlreadyExists())) {
            applyStatusChange(review, ReviewStatus.ALREADY_EXISTS, username);
        }

        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse recordUsageDecision(UUID reviewId, UsageDecisionRequest request, String authorization) {
        String username = currentUserService.requireUsername(authorization);
        Review review = lockReviewOrThrow(reviewId);

        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Izmaiņas iespējamas tikai stāvoklī “Apstrādē”.");
        }
        if (review.getType() != ReviewType.ENTRY) {
            throw new ConflictException("Lietojuma lēmums nepieciešams tikai jaunam šķirklim.");
        }

        if (request.decision() == UsageDecision.NOT_CONFIRMED) {
            applyStatusChange(review, ReviewStatus.INSUFFICIENT_DATA, username);
        } else if (request.decision() == UsageDecision.INVENTED) {
            applyStatusChange(review, ReviewStatus.INVENTED, username);
        }

        review.setUsageDecision(request.decision());
        review.setUsageNote(request.note());
        review.setUsageDecidedBy(username);
        review.setUsageDecidedAt(Instant.now());

        if (request.decision() == UsageDecision.USED) {
            entryService.create(reviewId);
        }

        return ReviewResponse.from(review);
    }

    private void applyStatusChange(Review review, ReviewStatus newStatus, String username) {
        ReviewStatus currentStatus = review.getStatus();

        if (currentStatus == ReviewStatus.IN_PROGRESS && newStatus == ReviewStatus.IN_PROGRESS) {
            if (review.getLastStatusChangedBy() == null) {
                review.setLastStatusChangedBy(username);
            }
            return;
        }
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new ConflictException("Izvēlētā darbība nav atļauta pašreizējā stāvoklī.");
        }
        if (newStatus == ReviewStatus.ALREADY_EXISTS) {
            validateMatchingEntry(review.getType(), review.getMatchedEntryId());
        }
        if (newStatus == ReviewStatus.COMPLETED) {
            validateCompletion(review);
        }

        review.setStatus(newStatus);
        review.setLastStatusChangedBy(username);
    }

    private void validateTypeChange(Review review, UpdateReviewTypeRequest request) {
        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Izmaiņas iespējamas tikai stāvoklī “Apstrādē”.");
        }
        if (request.type() == null) {
            throw new ConflictException("Norādiet, vai jāveido jauns šķirklis vai nozīme.");
        }
        if (request.type() != ReviewType.SENSE && request.matchedEntryId() != null) {
            throw new ConflictException("Esošu Tēzaura šķirkli var piesaistīt tikai nozīmes izskatīšanai.");
        }
        if (Boolean.TRUE.equals(request.meaningAlreadyExists()) && request.type() != ReviewType.SENSE) {
            throw new ConflictException("Lai atzīmētu nozīmi kā jau esošu, izvēlieties atbilstošo Tēzaura šķirkli.");
        }
        if (request.type() == ReviewType.SENSE) {
            validateMatchingEntry(request.type(), request.matchedEntryId());
        }
    }

    private void validateMatchingEntry(ReviewType type, Integer matchedEntryId) {
        if (type != ReviewType.SENSE || matchedEntryId == null) {
            throw new ConflictException("Izvēlieties atbilstošo Tēzaura šķirkli.");
        }
        if (!tezaursEntryRepository.existsById(matchedEntryId)) {
            throw new ConflictException("Izvēlētais Tēzaura šķirklis netika atrasts.");
        }
    }

    private void validateCompletion(Review review) {
        if (review.getType() == ReviewType.UNKNOWN) {
            throw new ConflictException("Vispirms veiciet pārbaudi Tēzaurā.");
        }
        if (review.getType() == ReviewType.ENTRY) {
            if (review.getUsageDecision() != UsageDecision.USED) {
                throw new ConflictException("Vispirms apstipriniet vārda lietojumu.");
            }
            if (!entryService.isReady(review.getId())) {
                throw new ConflictException("Saglabājiet šķirkļa melnrakstu ar vienu galveno leksēmu.");
            }
        }

        List<Sense> senses = senseRepository.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(
                review.getId(), review.getMatchedEntryId());
        if (senses.stream().noneMatch(Sense::isApprovedDraft)) {
            throw new ConflictException("Pievienojiet melnrakstam vismaz vienu jaunu nozīmi.");
        }
    }

    private String generatePublicId() {
        StringBuilder code = new StringBuilder(PUBLIC_ID_LENGTH);
        for (int character = 0; character < PUBLIC_ID_LENGTH; character++) {
            code.append(PUBLIC_ID_ALPHABET.charAt(random.nextInt(PUBLIC_ID_ALPHABET.length())));
        }

        return code.toString();
    }

    private Review lockReviewOrThrow(UUID reviewId) {
        return reviewRepository.findLockedById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));
    }

    private Review getReviewOrThrow(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));
    }

}
