package lv.tezaurs.tip.entry.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.review.service.ReviewService;
import lv.tezaurs.tip.entry.dto.EntryResponse;
import lv.tezaurs.tip.entry.dto.SaveEntryRequest;
import lv.tezaurs.tip.entry.dto.SaveEntryResponse;
import lv.tezaurs.tip.entry.service.EntryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/entry")
@RequiredArgsConstructor
public class EntryController {
    private final EntryService service;
    private final ReviewService reviewService;

    @GetMapping
    ResponseEntity<EntryResponse> find(@PathVariable UUID reviewId) {
        return service.find(reviewId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    SaveEntryResponse update(
            @PathVariable UUID reviewId, @Valid @RequestBody SaveEntryRequest request) {
        EntryResponse entry = service.update(reviewId, request);
        return new SaveEntryResponse(entry, reviewService.findById(reviewId));
    }

    @PostMapping
    SaveEntryResponse create(@PathVariable UUID reviewId) {
        EntryResponse entry = service.create(reviewId);
        return new SaveEntryResponse(entry, reviewService.findById(reviewId));
    }
}
