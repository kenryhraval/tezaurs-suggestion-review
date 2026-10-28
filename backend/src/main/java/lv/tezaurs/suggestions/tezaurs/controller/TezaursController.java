package lv.tezaurs.suggestions.tezaurs.controller;

import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryDetailsResponse;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursFormOptionsResponse;
import lv.tezaurs.suggestions.tezaurs.service.TezaursEntryDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tezaurs")
@RequiredArgsConstructor
public class TezaursController {
    private final TezaursEntryDetailsService service;

    @GetMapping("/form-options")
    TezaursFormOptionsResponse formOptions() {
        return service.formOptions();
    }

    @GetMapping("/entries/{entryId}")
    TezaursEntryDetailsResponse entry(@PathVariable Integer entryId) {
        return service.findEntry(entryId);
    }
}
