package lv.tezaurs.suggestions.tezaurs.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursSenseResponse;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursFormOptionsResponse;
import lv.tezaurs.suggestions.tezaurs.service.TezaursService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tezaurs")
@RequiredArgsConstructor
public class TezaursController {
    private final TezaursService service;

    @GetMapping("/form-options")
    TezaursFormOptionsResponse formOptions() {
        return service.formOptions();
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
