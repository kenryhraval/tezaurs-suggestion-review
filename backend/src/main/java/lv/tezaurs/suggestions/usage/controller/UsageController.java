package lv.tezaurs.suggestions.usage.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.usage.dto.Corpus;
import lv.tezaurs.suggestions.usage.dto.CorpusSearchResponse;
import lv.tezaurs.suggestions.usage.dto.EvidenceResponse;
import lv.tezaurs.suggestions.usage.dto.SaveEvidenceRequest;
import lv.tezaurs.suggestions.usage.dto.UsageDecisionRequest;
import lv.tezaurs.suggestions.usage.service.UsageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{caseId}/usage")
@RequiredArgsConstructor
public class UsageController {
    private final UsageService service;

    @GetMapping("/search")
    CorpusSearchResponse search(@PathVariable UUID caseId,
            @RequestParam @NotBlank @Size(max = 255) String query,
            @RequestParam(defaultValue = "LVK2022") Corpus corpus,
            @RequestParam(defaultValue = "1") @Min(1) int page) {
        return service.search(caseId, query, corpus, page);
    }

    @PostMapping("/decision")
    ReviewResponse decide(@PathVariable UUID caseId, @Valid @RequestBody UsageDecisionRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.decide(caseId, request, authorization);
    }

    @GetMapping("/evidence")
    List<EvidenceResponse> evidence(@PathVariable UUID caseId) {
        return service.evidence(caseId);
    }

    @PostMapping("/evidence")
    EvidenceResponse addEvidence(@PathVariable UUID caseId, @Valid @RequestBody SaveEvidenceRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.addEvidence(caseId, request, authorization);
    }

    @DeleteMapping("/evidence/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEvidence(@PathVariable UUID caseId, @PathVariable UUID evidenceId) {
        service.deleteEvidence(caseId, evidenceId);
    }
}
