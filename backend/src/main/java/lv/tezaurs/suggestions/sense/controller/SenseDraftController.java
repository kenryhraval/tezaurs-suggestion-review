package lv.tezaurs.suggestions.sense.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.sense.dto.SaveSenseDraftsRequest;
import lv.tezaurs.suggestions.sense.dto.SaveSenseDraftsResponse;
import lv.tezaurs.suggestions.sense.dto.SenseDraftResponse;
import lv.tezaurs.suggestions.sense.service.SenseDraftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/sense-drafts")
@RequiredArgsConstructor
public class SenseDraftController {
    private final SenseDraftService service;

    @GetMapping
    List<SenseDraftResponse> findAll(@PathVariable UUID reviewId) {
        return service.findAll(reviewId);
    }

    @PutMapping
    SaveSenseDraftsResponse save(
            @PathVariable UUID reviewId, @Valid @RequestBody SaveSenseDraftsRequest request) {
        return service.save(reviewId, request);
    }
}
