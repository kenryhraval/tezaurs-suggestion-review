package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {
    private final IncubatorSuggestionRepository sourceSuggestionRepository;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public List<SuggestionResponse> list() {
        return sourceSuggestionRepository.findAllForReviewQueue().stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional
    public SuggestionResponse changeStatus(Integer sourceSuggestionId, UpdateStatusRequest request) {
        IncubatorSuggestion source = requireSourceSuggestion(sourceSuggestionId);
        ReviewSuggestion review = reviewService.changeStatus(source, request.status());
        return SuggestionResponse.fromReview(source, review);
    }

    private IncubatorSuggestion requireSourceSuggestion(Integer id) {
        return sourceSuggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Suggestion " + id + " was not found"));
    }
}
