package lv.tezaurs.suggestions.suggestion.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.CreateSuggestionRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordCorpusCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.service.SuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionController {
    private final SuggestionService service;

    @PostMapping
    ResponseEntity<SuggestionResponse> create(@Valid @RequestBody CreateSuggestionRequest request) {
        SuggestionResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/suggestions/" + response.id())).body(response);
    }

    @GetMapping
    List<SuggestionResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    SuggestionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/status")
    SuggestionResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @PostMapping("/{id}/term-correction")
    SuggestionResponse correctTerm(@PathVariable UUID id, @Valid @RequestBody CorrectTermRequest request) {
        return service.correctTerm(id, request);
    }

    @PostMapping("/{id}/tezaurs-check")
    SuggestionResponse recordTezaursCheck(@PathVariable UUID id,
                                          @Valid @RequestBody RecordTezaursCheckRequest request) {
        return service.recordTezaursCheck(id, request);
    }

    @PostMapping("/{id}/corpus-check")
    SuggestionResponse recordCorpusCheck(@PathVariable UUID id,
                                         @Valid @RequestBody RecordCorpusCheckRequest request) {
        return service.recordCorpusCheck(id, request);
    }
}
