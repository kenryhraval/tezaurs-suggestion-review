package lv.tezaurs.tip.suggestion.controller;

import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.suggestion.dto.SuggestionCountsResponse;
import lv.tezaurs.tip.suggestion.service.SuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
