package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.entry.service.EntryDraftService;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.DuplicateReviewCandidateResponse;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewListItemResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.CaseSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.suggestion.repository.CaseSuggestionRepository;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursEntrySearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewCompletionService completionService;
    private final ReviewCaseRepository reviewRepository;
    private final CaseSuggestionRepository reviewSourceRepository;
    private final TezaursEntryRepository tezaursEntryRepository;
    private final TezaursEntrySearchService tezaursEntrySearchService;
    private final EntryDraftService entryDraftService;

    @Transactional(readOnly = true)
    public ReviewPageResponse list(
            SuggestionStatus status, String query, int page, int size, ReviewSort sort) {
        String searchQuery = query == null ? "" : query.trim();
        Sort reviewSort = switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case MOST_SUGGESTIONS -> Sort.by(Sort.Direction.DESC, "sourceCount")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        };
        reviewSort = reviewSort.and(Sort.by("id"));
        return ReviewPageResponse.from(reviewRepository.findForReviewQueue(
                        status, searchQuery, PageRequest.of(page, size, reviewSort))
                .map(ReviewListItemResponse::from));
    }

    @Transactional(readOnly = true)
    public ReviewResponse find(UUID reviewId) {
        return completionService.response(requireReview(reviewId));
    }

    @Transactional
    public ReviewCase startReview(IncubatorSuggestion source, SuggestionStatus status) {
        if (reviewSourceRepository.existsById(source.getId())) {
            throw new ConflictException("The source suggestion already belongs to a review");
        }
        ReviewCase review = create(source);
        review.changeStatus(status);
        return review;
    }

    @Transactional
    public ReviewResponse changeStatus(UUID reviewId, UpdateStatusRequest request) {
        ReviewCase review = requireReview(reviewId);
        if (request.status() == SuggestionStatus.COMPLETED) {
            completionService.requireCompletionAllowed(review);
        }
        review.changeStatus(request.status());
        return completionService.response(review);
    }

    @Transactional(readOnly = true)
    public List<DuplicateReviewCandidateResponse> findDuplicateCandidates(String query, UUID excludedCaseId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return reviewRepository.findDuplicateCandidates(
                        excludedCaseId, SuggestionStatus.GARBAGE, query.trim(),
                        PageRequest.of(0, 5, Sort.by("reviewedTerm")))
                .stream()
                .map(review -> DuplicateReviewCandidateResponse.from(
                        review, reviewSourceRepository.countByReviewCaseId(review.getId())))
                .toList();
    }

    @Transactional
    public ReviewResponse linkDuplicate(IncubatorSuggestion source, UUID targetCaseId) {
        ReviewCase target = requireReview(targetCaseId);
        Optional<CaseSuggestion> existingLink = reviewSourceRepository.findById(source.getId());
        if (existingLink.isPresent()) {
            ReviewCase sourceCase = existingLink.get().getReviewCase();
            if (sourceCase.getId().equals(targetCaseId)) {
                throw new ConflictException("A case cannot be merged into itself");
            }
            for (CaseSuggestion link : List.copyOf(sourceCase.getSources())) {
                link.reassignTo(target);
            }
            // Persist the moved links before deleting the case with ON DELETE CASCADE.
            reviewSourceRepository.flush();
            reviewRepository.delete(sourceCase);
        } else {
            reviewSourceRepository.save(new CaseSuggestion(source, target));
        }
        return completionService.response(target);
    }

    @Transactional
    public ReviewResponse detachSuggestion(UUID reviewId, Integer sourceId) {
        ReviewCase review = reviewRepository.findLockedById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review " + reviewId + " was not found"));
        CaseSuggestion link = review.getSources().stream()
                .filter(item -> item.getSourceSuggestion().getId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("The suggestion does not belong to this review"));
        if (review.getSources().size() <= 1) {
            throw new ConflictException("The last suggestion cannot be detached from a case");
        }
        review.getSources().remove(link);
        reviewSourceRepository.delete(link);
        return completionService.response(review);
    }

    @Transactional
    public ReviewResponse correctTerm(UUID reviewId, CorrectTermRequest request) {
        ReviewCase review = requireReview(reviewId);
        review.correctTerm(request.reviewedTerm().trim());
        return completionService.response(review);
    }

    @Transactional
    public ReviewResponse recordTezaursCheck(UUID reviewId, RecordTezaursCheckRequest request) {
        ReviewCase review = requireReview(reviewId);
        if (request.status().entryExists()
                && (request.matchedEntryId() == null
                || !tezaursEntryRepository.existsById(request.matchedEntryId()))) {
            throw new ConflictException("The selected Tēzaurs entry does not exist");
        }
        review.recordTezaursCheck(request.status(), request.matchedEntryId());
        if (request.status() == TezaursStatus.NOT_FOUND) {
            entryDraftService.create(reviewId);
        }
        return completionService.response(review);
    }

    @Transactional(readOnly = true)
    public List<TezaursEntryCandidateResponse> findTezaursCandidates(UUID reviewId, String query) {
        ReviewCase review = requireReview(reviewId);
        String searchTerm = query == null || query.isBlank()
                ? review.getReviewedTerm() : query.trim();
        return tezaursEntrySearchService.findMatches(searchTerm);
    }

    private ReviewCase requireReview(UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }

    private ReviewCase create(IncubatorSuggestion source) {
        ReviewCase review = reviewRepository.save(
                new ReviewCase(source.getLemma()));
        reviewSourceRepository.save(
                new CaseSuggestion(source, review));
        return review;
    }
}
