package lv.tezaurs.tip.sense.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.sense.dto.MergeSenseRequest;
import lv.tezaurs.tip.sense.dto.SaveSenseRequest;
import lv.tezaurs.tip.sense.dto.SenseResponse;
import lv.tezaurs.tip.sense.service.SenseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/senses")
@RequiredArgsConstructor
public class SenseController {
    private final SenseService service;

    @GetMapping
    List<SenseResponse> findAll(@PathVariable UUID reviewId) {
        return service.findAllForReview(reviewId);
    }

    @PostMapping
    SenseResponse create(@PathVariable UUID reviewId, @Valid @RequestBody SaveSenseRequest request) {
        return service.create(reviewId, request);
    }

    @PutMapping("/{senseId}")
    SenseResponse update(@PathVariable UUID reviewId, @PathVariable UUID senseId,
            @Valid @RequestBody SaveSenseRequest request) {
        return service.update(reviewId, senseId, request);
    }

    @PostMapping("/{senseId}/merge")
    SenseResponse merge(@PathVariable UUID reviewId, @PathVariable UUID senseId,
            @Valid @RequestBody MergeSenseRequest request) {
        return service.merge(reviewId, senseId, request.targetSenseId());
    }

    @DeleteMapping("/{senseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID reviewId, @PathVariable UUID senseId) {
        service.delete(reviewId, senseId);
    }

}
