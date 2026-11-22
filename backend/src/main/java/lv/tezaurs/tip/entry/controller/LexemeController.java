package lv.tezaurs.tip.entry.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.review.service.ReviewService;
import lv.tezaurs.tip.review.dto.ReviewResponse;
import lv.tezaurs.tip.entry.dto.EntryResponse.LexemeResponse;
import lv.tezaurs.tip.entry.dto.SaveLexemeRequest;
import lv.tezaurs.tip.entry.dto.SaveLexemeResponse;
import lv.tezaurs.tip.entry.service.LexemeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/entry/lexemes")
@RequiredArgsConstructor
public class LexemeController {
    private final LexemeService service;
    private final ReviewService reviewService;

    @PostMapping
    SaveLexemeResponse create(
            @PathVariable UUID reviewId, @Valid @RequestBody SaveLexemeRequest request) {
        LexemeResponse lexeme = service.create(reviewId, request);
        return new SaveLexemeResponse(lexeme, reviewService.findById(reviewId));
    }

    @PutMapping("/{lexemeId}")
    SaveLexemeResponse update(
            @PathVariable UUID reviewId,
            @PathVariable UUID lexemeId,
            @Valid @RequestBody SaveLexemeRequest request) {
        LexemeResponse lexeme = service.update(reviewId, lexemeId, request);
        return new SaveLexemeResponse(lexeme, reviewService.findById(reviewId));
    }

    @DeleteMapping("/{lexemeId}")
    ReviewResponse delete(@PathVariable UUID reviewId, @PathVariable UUID lexemeId) {
        service.delete(reviewId, lexemeId);
        return reviewService.findById(reviewId);
    }
}
