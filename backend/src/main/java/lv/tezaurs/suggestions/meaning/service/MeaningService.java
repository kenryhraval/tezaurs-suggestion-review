package lv.tezaurs.suggestions.meaning.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.dto.MeaningResponse;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.repository.MeaningRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the single meaning revision chain for each suggestion. */
@Service
@RequiredArgsConstructor
public class MeaningService {
    private final MeaningRepository meaningRepository;
    private final SuggestionRepository suggestionRepository;

    @Transactional(readOnly = true)
    public List<MeaningResponse> list(UUID suggestionId) {
        requireSuggestion(suggestionId);
        return meaningRepository.findAllBySuggestionIdOrderByCreatedAtAscIdAsc(suggestionId).stream()
                .map(MeaningResponse::from)
                .toList();
    }

    @Transactional
    public MeaningResponse revise(UUID suggestionId, UUID meaningId, CreateMeaningRevisionRequest request) {
        Meaning source = requireMeaning(suggestionId, meaningId);
        Meaning revision = source.revise(request.gloss().trim());
        meaningRepository.flush();
        return MeaningResponse.from(meaningRepository.saveAndFlush(revision));
    }

    private void requireSuggestion(UUID suggestionId) {
        if (!suggestionRepository.existsById(suggestionId)) {
            throw new NotFoundException("Suggestion " + suggestionId + " was not found");
        }
    }

    private Meaning requireMeaning(UUID suggestionId, UUID meaningId) {
        return meaningRepository.findByIdAndSuggestionId(meaningId, suggestionId)
                .orElseThrow(() -> new NotFoundException("Meaning " + meaningId + " was not found"));
    }
}
