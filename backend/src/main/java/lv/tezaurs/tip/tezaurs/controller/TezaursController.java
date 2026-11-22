package lv.tezaurs.tip.tezaurs.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.tip.tezaurs.dto.TezaursFormOptionsResponse;
import lv.tezaurs.tip.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.tip.tezaurs.service.TezaursEntrySearchService;
import lv.tezaurs.tip.tezaurs.service.TezaursService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tezaurs")
@RequiredArgsConstructor
public class TezaursController {
    private final TezaursService service;
    private final TezaursEntrySearchService searchService;

    @GetMapping("/form-options")
    TezaursFormOptionsResponse formOptions() {
        return service.formOptions();
    }

    @GetMapping("/entries")
    List<TezaursEntryCandidateResponse> searchEntries(@RequestParam String query) {
        return searchService.findMatches(query);
    }

    @GetMapping("/entries/{entryId}")
    TezaursEntryCandidateResponse entry(@PathVariable Integer entryId) {
        return service.findEntry(entryId);
    }

    @GetMapping("/entries/{entryId}/senses")
    List<TezaursSenseResponse> senses(@PathVariable Integer entryId) {
        return service.findSenses(entryId);
    }
}
