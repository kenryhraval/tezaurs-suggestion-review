package lv.tezaurs.tip.entry.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.entry.dto.EntryResponse.LexemeResponse;
import lv.tezaurs.tip.entry.dto.SaveLexemeRequest;
import lv.tezaurs.tip.entry.service.LexemeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/entry/lexemes")
@RequiredArgsConstructor
public class LexemeController {
    private final LexemeService service;

    @PostMapping
    LexemeResponse create(
            @PathVariable UUID reviewId, @Valid @RequestBody SaveLexemeRequest request) {
        return service.create(reviewId, request);
    }

    @PutMapping("/{lexemeId}")
    LexemeResponse update(
            @PathVariable UUID reviewId,
            @PathVariable UUID lexemeId,
            @Valid @RequestBody SaveLexemeRequest request) {
        return service.update(reviewId, lexemeId, request);
    }

    @DeleteMapping("/{lexemeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID reviewId, @PathVariable UUID lexemeId) {
        service.delete(reviewId, lexemeId);
    }
}
