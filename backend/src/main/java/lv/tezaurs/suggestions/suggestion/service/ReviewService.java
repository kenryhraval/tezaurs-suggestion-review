package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestionSource;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionSourceRepository;
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
    private final ReviewSuggestionRepository reviewRepository;
    private final ReviewSuggestionSourceRepository reviewSourceRepository;
    private final TezaursEntryRepository tezaursEntryRepository;
    private final TezaursEntrySearchService tezaursEntrySearchService;

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
        return ReviewResponse.from(requireReview(reviewId));
    }

    @Transactional
    public ReviewSuggestion startReview(IncubatorSuggestion source, SuggestionStatus status) {
        if (reviewSourceRepository.existsById(source.getId())) {
            throw new ConflictException("The source suggestion already belongs to a review");
        }
        ReviewSuggestion review = create(source);
        review.changeStatus(status);
        return review;
    }

    @Transactional
    public ReviewResponse changeStatus(UUID reviewId, UpdateStatusRequest request) {
        ReviewSuggestion review = requireReview(reviewId);
        review.changeStatus(request.status());
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<DuplicateReviewCandidateResponse> findDuplicateCandidates(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return reviewRepository.findByReviewedTermContainingIgnoreCase(
                        query.trim(), PageRequest.of(0, 5, Sort.by("reviewedTerm")))
                .stream()
                .map(review -> DuplicateReviewCandidateResponse.from(
                        review, reviewSourceRepository.countByReviewId(review.getId())))
                .toList();
    }

    @Transactional
    public void linkDuplicate(IncubatorSuggestion source, UUID reviewId) {
        if (reviewSourceRepository.existsById(source.getId())) {
            throw new ConflictException("The source suggestion already belongs to a review");
        }
        ReviewSuggestion review = requireReview(reviewId);
        reviewSourceRepository.save(
                new ReviewSuggestionSource(source, review));
    }

    @Transactional
    public ReviewResponse correctTerm(UUID reviewId, CorrectTermRequest request) {
        ReviewSuggestion review = requireReview(reviewId);
        review.correctTerm(request.reviewedTerm().trim());
        return ReviewResponse.from(review);
    }

    @Transactional
    public ReviewResponse recordTezaursCheck(UUID reviewId, RecordTezaursCheckRequest request) {
        ReviewSuggestion review = requireReview(reviewId);
        if (request.status().entryExists()
                && (request.matchedEntryId() == null
                || !tezaursEntryRepository.existsById(request.matchedEntryId()))) {
            throw new ConflictException("The selected Tēzaurs entry does not exist");
        }
        review.recordTezaursCheck(request.status(), request.matchedEntryId());
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<TezaursEntryCandidateResponse> findTezaursCandidates(UUID reviewId, String query) {
        ReviewSuggestion review = requireReview(reviewId);
        String searchTerm = query == null || query.isBlank()
                ? review.getReviewedTerm() : query.trim();
        return tezaursEntrySearchService.findMatches(searchTerm);
    }

    private ReviewSuggestion requireReview(UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review " + id + " was not found"));
    }

    private ReviewSuggestion create(IncubatorSuggestion source) {
        ReviewSuggestion review = reviewRepository.save(
                new ReviewSuggestion(source.getLemma()));
        reviewSourceRepository.save(
                new ReviewSuggestionSource(source, review));
        return review;
    }
}
