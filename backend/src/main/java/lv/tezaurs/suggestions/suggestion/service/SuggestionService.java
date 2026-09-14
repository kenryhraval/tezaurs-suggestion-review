package lv.tezaurs.suggestions.suggestion.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.repository.MeaningRepository;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.CreateSuggestionRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordCorpusCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles the initial suggestion review operations. */
@Service
@RequiredArgsConstructor
public class SuggestionService {
    private final SuggestionRepository suggestionRepository;
    private final MeaningRepository meaningRepository;

    @Transactional
    public SuggestionResponse create(CreateSuggestionRequest request) {
        Suggestion suggestion = new Suggestion(request.term().trim(),
                trimToNull(request.usageExample()), trimToNull(request.notes()), trimToNull(request.submitterName()),
                trimToNull(request.submitterEmail()));
        Suggestion saved = suggestionRepository.saveAndFlush(suggestion);
        Meaning submittedMeaning = new Meaning(saved.getId(), null, MeaningOrigin.SUBMITTER,
                request.definition().trim());
        meaningRepository.save(submittedMeaning);
        return SuggestionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> list() {
        return suggestionRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SuggestionResponse get(UUID id) {
        return SuggestionResponse.from(requireSuggestion(id));
    }

    @Transactional
    public SuggestionResponse changeStatus(UUID id, UpdateStatusRequest request) {
        Suggestion suggestion = requireSuggestion(id);
        suggestion.changeStatus(request.status());
        suggestionRepository.flush();
        return SuggestionResponse.from(suggestion);
    }

    @Transactional
    public SuggestionResponse correctTerm(UUID id, CorrectTermRequest request) {
        Suggestion suggestion = requireSuggestion(id);
        suggestion.correctTerm(request.reviewedTerm().trim());
        suggestionRepository.flush();
        return SuggestionResponse.from(suggestion);
    }

    @Transactional
    public SuggestionResponse recordTezaursCheck(UUID id, RecordTezaursCheckRequest request) {
        Suggestion suggestion = requireSuggestion(id);
        suggestion.recordTezaursCheck(request.status(), request.matchedEntryId());
        suggestionRepository.flush();
        return SuggestionResponse.from(suggestion);
    }

    @Transactional
    public SuggestionResponse recordCorpusCheck(UUID id, RecordCorpusCheckRequest request) {
        Suggestion suggestion = requireSuggestion(id);
        suggestion.recordCorpusCheck(request.status());
        suggestionRepository.flush();
        return SuggestionResponse.from(suggestion);
    }

    private Suggestion requireSuggestion(UUID id) {
        return suggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Suggestion " + id + " was not found"));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
