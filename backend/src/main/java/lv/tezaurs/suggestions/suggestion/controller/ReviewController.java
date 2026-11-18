package lv.tezaurs.suggestions.suggestion.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.DuplicateReviewCandidateResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateCaseTypeRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.dto.LinkDuplicateRequest;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.suggestion.service.ReviewService;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) List<CaseType> types,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "NEWEST") ReviewSort sort) {
        return service.list(status, types, query, page, size, sort);
    }

    @GetMapping("/{reviewId}")
    ReviewResponse find(@PathVariable UUID reviewId) {
        return service.find(reviewId);
    }

    @GetMapping("/by-public-id/{publicId}")
    ReviewResponse findByPublicId(@PathVariable @Pattern(regexp = "[a-z0-9]{6,}") String publicId) {
        return service.findByPublicId(publicId);
    }

    @PostMapping("/{reviewId}/status")
    ReviewResponse changeStatus(
            @PathVariable UUID reviewId, @Valid @RequestBody UpdateStatusRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.changeStatus(reviewId, request, authorization);
    }

    @GetMapping("/duplicate-candidates")
    List<DuplicateReviewCandidateResponse> findDuplicateCandidates(
            @RequestParam String query, @RequestParam(required = false) UUID excludedCaseId) {
        return service.findDuplicateCandidates(query, excludedCaseId);
    }

    @PostMapping("/{reviewId}/duplicate")
    ReviewResponse linkDuplicate(@PathVariable UUID reviewId, @Valid @RequestBody LinkDuplicateRequest request) {
        return service.linkDuplicate(reviewId, request.reviewId());
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

    @PostMapping("/{reviewId}/type")
    ReviewResponse changeType(@PathVariable UUID reviewId,
                              @Valid @RequestBody UpdateCaseTypeRequest request,
                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.changeType(reviewId, request, authorization);
    }

    @GetMapping("/{reviewId}/tezaurs-candidates")
    List<TezaursEntryCandidateResponse> findTezaursCandidates(
            @PathVariable UUID reviewId,
            @RequestParam(required = false) String query) {
        return service.findTezaursCandidates(reviewId, query);
    }

}
