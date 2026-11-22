package lv.tezaurs.tip.entry.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.entry.dto.EntryResponse;
import lv.tezaurs.tip.entry.dto.SaveEntryRequest;
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

    @GetMapping
    ResponseEntity<EntryResponse> find(@PathVariable UUID reviewId) {
        return service.find(reviewId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    EntryResponse update(
            @PathVariable UUID reviewId, @Valid @RequestBody SaveEntryRequest request) {
        return service.update(reviewId, request);
    }

    @PostMapping
    EntryResponse create(@PathVariable UUID reviewId) {
        return service.create(reviewId);
    }
}
