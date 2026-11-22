package lv.tezaurs.tip.review.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.review.dto.ReviewPageResponse;
import lv.tezaurs.tip.review.dto.ReviewResponse;
import lv.tezaurs.tip.review.dto.ReviewSort;
import lv.tezaurs.tip.review.dto.DuplicateReviewCandidateResponse;
import lv.tezaurs.tip.review.dto.LinkDuplicateRequest;
import lv.tezaurs.tip.review.dto.UpdateReviewTitleRequest;
import lv.tezaurs.tip.review.dto.UpdateReviewTypeRequest;
import lv.tezaurs.tip.review.dto.UpdateStatusRequest;
import lv.tezaurs.tip.review.dto.UsageDecisionRequest;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.service.ReviewService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
            @RequestParam(required = false) List<ReviewType> types,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "NEWEST") ReviewSort sort) {
        return service.list(status, types, query, page, size, sort);
    }

    @GetMapping("/{reviewId}")
    ReviewResponse findById(@PathVariable UUID reviewId) {
        return service.findById(reviewId);
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
            @RequestParam String query, @RequestParam(value = "excludedCaseId", required = false) UUID excludedReviewId) {
        return service.findDuplicateCandidates(query, excludedReviewId);
    }

    @PostMapping("/{reviewId}/duplicate")
    ReviewResponse mergeReviews(@PathVariable UUID reviewId, @Valid @RequestBody LinkDuplicateRequest request) {
        return service.mergeReviews(reviewId, request.reviewId());
    }

    @PostMapping("/{reviewId}/term-correction")
    ReviewResponse updateTitle(@PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewTitleRequest request) {
        return service.updateTitle(reviewId, request);
    }

    @DeleteMapping("/{reviewId}/suggestions/{suggestionId}")
    ReviewResponse moveSuggestionToNewReview(@PathVariable UUID reviewId, @PathVariable Integer suggestionId) {
        return service.moveSuggestionToNewReview(reviewId, suggestionId);
    }

    @PostMapping("/{reviewId}/type")
    ReviewResponse changeType(@PathVariable UUID reviewId,
                              @Valid @RequestBody UpdateReviewTypeRequest request,
                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.changeType(reviewId, request, authorization);
    }

    @PostMapping("/{reviewId}/usage/decision")
    ReviewResponse recordUsageDecision(@PathVariable UUID reviewId, @Valid @RequestBody UsageDecisionRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.recordUsageDecision(reviewId, request, authorization);
    }

}
