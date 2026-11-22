package lv.tezaurs.tip.sense.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.review.service.ReviewService;
import lv.tezaurs.tip.sense.dto.SaveSenseRequest;
import lv.tezaurs.tip.sense.dto.SaveSenseResponse;
import lv.tezaurs.tip.review.dto.ReviewResponse;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.sense.service.SenseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/senses")
@RequiredArgsConstructor
public class SenseController {
    private final SenseService service;
    private final ReviewService reviewService;

    @GetMapping
    List<SenseResponse> findAll(@PathVariable UUID reviewId) {
        return service.findAllForReview(reviewId);
    }

    @PostMapping
    SaveSenseResponse create(@PathVariable UUID reviewId, @Valid @RequestBody SaveSenseRequest request) {
        SenseResponse sense = service.create(reviewId, request);
        return new SaveSenseResponse(sense, reviewService.findById(reviewId));
    }

    @PutMapping("/{senseId}")
    SaveSenseResponse update(@PathVariable UUID reviewId, @PathVariable UUID senseId,
            @Valid @RequestBody SaveSenseRequest request) {
        SenseResponse sense = service.update(reviewId, senseId, request);
        return new SaveSenseResponse(sense, reviewService.findById(reviewId));
    }

    @DeleteMapping("/{senseId}")
    ReviewResponse delete(@PathVariable UUID reviewId, @PathVariable UUID senseId) {
        service.delete(reviewId, senseId);
        return reviewService.findById(reviewId);
    }

}
