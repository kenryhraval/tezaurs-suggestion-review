package lv.tezaurs.suggestions.meaning.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.dto.MeaningResponse;
import lv.tezaurs.suggestions.meaning.service.MeaningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/meanings")
@RequiredArgsConstructor
public class MeaningController {
    private final MeaningService service;

    @GetMapping
    List<MeaningResponse> list(@PathVariable UUID reviewId) {
        return service.list(reviewId);
    }

    @PostMapping("/{meaningId}/revision")
    ResponseEntity<MeaningResponse> revise(@PathVariable UUID reviewId, @PathVariable UUID meaningId,
                                            @Valid @RequestBody CreateMeaningRevisionRequest request) {
        MeaningResponse response = service.revise(reviewId, meaningId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
