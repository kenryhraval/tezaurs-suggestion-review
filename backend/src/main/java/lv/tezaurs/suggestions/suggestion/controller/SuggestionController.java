package lv.tezaurs.suggestions.suggestion.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionCountsResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.dto.LinkDuplicateRequest;
import lv.tezaurs.suggestions.suggestion.service.SuggestionService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionController {
    private final SuggestionService service;

    @GetMapping("/counts")
    SuggestionCountsResponse counts() {
        return service.counts();
    }

    @GetMapping
    SuggestionPageResponse list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "NEWEST") SuggestionSort sort) {
        return service.list(query, page, size, sort);
    }

    @PostMapping("/{id}/status")
    ReviewResponse changeStatus(@PathVariable Integer id, @Valid @RequestBody UpdateStatusRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.changeStatus(id, request, authorization);
    }

    @PostMapping("/{id}/duplicate")
    ReviewResponse linkDuplicate(@PathVariable Integer id, @Valid @RequestBody LinkDuplicateRequest request) {
        return service.linkDuplicate(id, request);
    }
}
