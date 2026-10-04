package lv.tezaurs.suggestions.suggestion.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.AddCorpusExampleRequest;
import lv.tezaurs.suggestions.suggestion.dto.CorpusExampleResponse;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService service;

    @GetMapping("/by-source-suggestion/{sourceSuggestionId}")
    ResponseEntity<ReviewResponse> findBySourceSuggestionId(@PathVariable Integer sourceSuggestionId) {
        return service.findBySourceSuggestionId(sourceSuggestionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{reviewId}/term-correction")
    ReviewResponse correctTerm(@PathVariable UUID reviewId,
                               @Valid @RequestBody CorrectTermRequest request) {
        return service.correctTerm(reviewId, request);
    }

    @PostMapping("/{reviewId}/tezaurs-check")
    ReviewResponse recordTezaursCheck(@PathVariable UUID reviewId,
                                      @Valid @RequestBody RecordTezaursCheckRequest request) {
        return service.recordTezaursCheck(reviewId, request);
    }

    @GetMapping("/{reviewId}/corpus-examples")
    List<CorpusExampleResponse> listCorpusExamples(@PathVariable UUID reviewId) {
        return service.listCorpusExamples(reviewId);
    }

    @PostMapping("/{reviewId}/corpus-examples")
    ResponseEntity<CorpusExampleResponse> addCorpusExample(
            @PathVariable UUID reviewId, @Valid @RequestBody AddCorpusExampleRequest request) {
        CorpusExampleResponse response = service.addCorpusExample(reviewId, request);
        return ResponseEntity.created(URI.create("/api/reviews/" + reviewId
                + "/corpus-examples/" + response.id())).body(response);
    }
}
