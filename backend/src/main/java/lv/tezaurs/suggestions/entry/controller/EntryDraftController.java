package lv.tezaurs.suggestions.entry.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.entry.dto.CreateEntryDraftRequest;
import lv.tezaurs.suggestions.entry.dto.EntryDraftResponse;
import lv.tezaurs.suggestions.entry.dto.SaveEntryDraftRequest;
import lv.tezaurs.suggestions.entry.dto.SaveEntryDraftResponse;
import lv.tezaurs.suggestions.entry.service.EntryDraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/entry-draft")
@RequiredArgsConstructor
public class EntryDraftController {
    private final EntryDraftService service;

    @GetMapping
    ResponseEntity<EntryDraftResponse> find(@PathVariable UUID reviewId) {
        return service.find(reviewId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    SaveEntryDraftResponse update(
            @PathVariable UUID reviewId, @Valid @RequestBody SaveEntryDraftRequest request) {
        return service.update(reviewId, request);
    }

    @PostMapping
    SaveEntryDraftResponse create(
            @PathVariable UUID reviewId, @Valid @RequestBody CreateEntryDraftRequest request) {
        return service.create(reviewId, request);
    }
}
