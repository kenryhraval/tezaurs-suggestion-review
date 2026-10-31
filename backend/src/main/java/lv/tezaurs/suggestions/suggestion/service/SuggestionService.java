package lv.tezaurs.suggestions.suggestion.service;

import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.LinkDuplicateRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {
    private final IncubatorSuggestionRepository sourceSuggestionRepository;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public SuggestionPageResponse list(String query, int page, int size, SuggestionSort sort) {
        String searchQuery = query == null ? "" : query.trim();
        Sort.Direction direction = sort == SuggestionSort.OLDEST
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(direction, "createdAt").and(Sort.by(direction, "id")));
        return SuggestionPageResponse.from(sourceSuggestionRepository
                .findUnstartedForReviewQueue(searchQuery, pageable)
                .map(SuggestionResponse::fromNewSuggestion));
    }

    @Transactional
    public ReviewResponse changeStatus(Integer sourceSuggestionId, UpdateStatusRequest request) {
        IncubatorSuggestion source = requireSourceSuggestion(sourceSuggestionId);
        ReviewSuggestion review = reviewService.startReview(source, request.status());
        return ReviewResponse.from(review);
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
