package lv.tezaurs.suggestions.suggestion.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.LinkDuplicateRequest;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionListItem;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {
    private final IncubatorSuggestionRepository sourceSuggestionRepository;
    private final ReviewSuggestionRepository reviewRepository;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public List<SuggestionResponse> list(SuggestionStatus status) {
        if (status == SuggestionStatus.NEW) {
            return findNewSuggestions();
        }
        if (status != null) {
            return mapReviews(reviewRepository.findForReviewQueue(status.name()));
        }

        List<SuggestionResponse> suggestions = new ArrayList<>(findNewSuggestions());
        suggestions.addAll(mapReviews(reviewRepository.findForReviewQueue(null)));
        return suggestions;
    }

    private List<SuggestionResponse> findNewSuggestions() {
        return sourceSuggestionRepository.findAllByReviewSourceIsNullOrderByCreatedAtAscIdAsc().stream()
                .map(SuggestionResponse::fromNewSuggestion)
                .toList();
    }

    private List<SuggestionResponse> mapReviews(List<SuggestionListItem> items) {
        return items.stream()
                .map(SuggestionResponse::from)
                .toList();
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
