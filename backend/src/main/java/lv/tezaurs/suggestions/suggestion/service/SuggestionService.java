package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates the workflow state of source suggestions. */
@Service
@RequiredArgsConstructor
public class SuggestionService {
    private static final int WEB_SUGGESTION_CHANNEL = 1;

    private final IncubatorSuggestionRepository incubatorSuggestionRepository;
    private final ReviewService reviewService;

    @Transactional(readOnly = true)
    public List<SuggestionResponse> list() {
        return incubatorSuggestionRepository
                .findAllByChannelIdOrderByCreatedAtAscIdAsc(WEB_SUGGESTION_CHANNEL).stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional
    public SuggestionResponse changeStatus(Integer id, UpdateStatusRequest request) {
        IncubatorSuggestion source = requireSourceSuggestionForUpdate(id);
        source.getStatus().requireTransitionTo(request.status());
        if (request.status().requiresReviewRecord()) {
            reviewService.ensureCreated(source);
        }
        source.changeStatus(request.status());
        incubatorSuggestionRepository.flush();
        return SuggestionResponse.from(source);
    }

    private IncubatorSuggestion requireSourceSuggestionForUpdate(Integer id) {
        return incubatorSuggestionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Suggestion " + id + " was not found"));
    }
}
