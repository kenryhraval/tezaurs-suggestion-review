package lv.tezaurs.suggestions.suggestion.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.LinkDuplicateRequest;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.service.SuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionController {
    private final SuggestionService service;

    @GetMapping
    List<SuggestionResponse> list(@RequestParam(required = false) SuggestionStatus status) {
        return service.list(status);
    }

    @PostMapping("/{id}/status")
    SuggestionResponse changeStatus(@PathVariable Integer id, @Valid @RequestBody UpdateStatusRequest request) {
        return service.changeStatus(id, request);
    }

    @PostMapping("/{id}/duplicate")
    ResponseEntity<Void> linkDuplicate(
            @PathVariable Integer id, @Valid @RequestBody LinkDuplicateRequest request) {
        service.linkDuplicate(id, request);
        return ResponseEntity.noContent().build();
    }

}
