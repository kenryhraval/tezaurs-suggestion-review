package lv.tezaurs.suggestions.entry.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.entry.dto.SaveLexemeDraftRequest;
import lv.tezaurs.suggestions.entry.dto.SaveLexemeDraftResponse;
import lv.tezaurs.suggestions.entry.service.LexemeDraftService;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/entry-draft/lexemes")
@RequiredArgsConstructor
public class LexemeDraftController {
    private final LexemeDraftService service;

    @PostMapping
    SaveLexemeDraftResponse create(
            @PathVariable UUID reviewId, @Valid @RequestBody SaveLexemeDraftRequest request) {
        return service.create(reviewId, request);
    }

    @PutMapping("/{lexemeId}")
    SaveLexemeDraftResponse update(
            @PathVariable UUID reviewId,
            @PathVariable UUID lexemeId,
            @Valid @RequestBody SaveLexemeDraftRequest request) {
        return service.update(reviewId, lexemeId, request);
    }

    @DeleteMapping("/{lexemeId}")
    ReviewResponse delete(@PathVariable UUID reviewId, @PathVariable UUID lexemeId) {
        return service.delete(reviewId, lexemeId);
    }
}
