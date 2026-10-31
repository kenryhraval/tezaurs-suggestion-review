package lv.tezaurs.suggestions.suggestion.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.DuplicateReviewCandidateResponse;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.service.ReviewService;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService service;

    @GetMapping
    ReviewPageResponse list(
            @RequestParam SuggestionStatus status,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "NEWEST") ReviewSort sort) {
        return service.list(status, query, page, size, sort);
    }

    @GetMapping("/{reviewId}")
    ReviewResponse find(@PathVariable UUID reviewId) {
        return service.find(reviewId);
    }

    @PostMapping("/{reviewId}/status")
    ReviewResponse changeStatus(
            @PathVariable UUID reviewId, @Valid @RequestBody UpdateStatusRequest request) {
        return service.changeStatus(reviewId, request);
    }

    @GetMapping("/duplicate-candidates")
    List<DuplicateReviewCandidateResponse> findDuplicateCandidates(
            @RequestParam String query, @RequestParam(required = false) UUID excludedCaseId) {
        return service.findDuplicateCandidates(query, excludedCaseId);
    }

    @PostMapping("/{reviewId}/term-correction")
    ReviewResponse correctTerm(@PathVariable UUID reviewId,
                               @Valid @RequestBody CorrectTermRequest request) {
        return service.correctTerm(reviewId, request);
    }

    @DeleteMapping("/{reviewId}/suggestions/{sourceId}")
    ReviewResponse detachSuggestion(@PathVariable UUID reviewId, @PathVariable Integer sourceId) {
        return service.detachSuggestion(reviewId, sourceId);
    }

    @PostMapping("/{reviewId}/tezaurs-check")
    ReviewResponse recordTezaursCheck(@PathVariable UUID reviewId,
                                      @Valid @RequestBody RecordTezaursCheckRequest request) {
        return service.recordTezaursCheck(reviewId, request);
    }

    @GetMapping("/{reviewId}/tezaurs-candidates")
    List<TezaursEntryCandidateResponse> findTezaursCandidates(
            @PathVariable UUID reviewId,
            @RequestParam(required = false) String query) {
        return service.findTezaursCandidates(reviewId, query);
    }

}
