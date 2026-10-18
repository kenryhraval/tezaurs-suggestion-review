package lv.tezaurs.suggestions.suggestion.service;

import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.LinkDuplicateRequest;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {
    private final IncubatorSuggestionRepository sourceSuggestionRepository;
    private final ReviewSuggestionRepository reviewRepository;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public SuggestionPageResponse list(
            SuggestionStatus status, String query, int page, int size, SuggestionSort sort) {
        String searchQuery = query == null ? "" : query.trim();

        if (status == SuggestionStatus.NEW) {
            Sort.Direction direction = sort == SuggestionSort.OLDEST
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            PageRequest pageable = PageRequest.of(page, size,
                    Sort.by(direction, "createdAt").and(Sort.by(direction, "id")));
            return SuggestionPageResponse.from(sourceSuggestionRepository
                    .findUnstartedForReviewQueue(searchQuery, pageable)
                    .map(SuggestionResponse::fromNewSuggestion));
        }

        Sort reviewSort = switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case MOST_SUGGESTIONS -> Sort.by(Sort.Direction.DESC, "sourceCount")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        };
        reviewSort = reviewSort.and(Sort.by("id"));

        return SuggestionPageResponse.from(reviewRepository.findForReviewQueue(
                        status, searchQuery, PageRequest.of(page, size, reviewSort))
                .map(SuggestionResponse::from));
    }

    @Transactional
    public SuggestionResponse changeStatus(Integer sourceSuggestionId, UpdateStatusRequest request) {
        IncubatorSuggestion source = requireSourceSuggestion(sourceSuggestionId);
        ReviewSuggestion review = reviewService.changeStatus(source, request.status());
        return SuggestionResponse.fromReview(source, review);
    }

    @Transactional
    public void linkDuplicate(Integer sourceSuggestionId, LinkDuplicateRequest request) {
        reviewService.linkDuplicate(requireSourceSuggestion(sourceSuggestionId), request.reviewId());
    }

    private IncubatorSuggestion requireSourceSuggestion(Integer id) {
        return sourceSuggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Suggestion " + id + " was not found"));
    }
}
