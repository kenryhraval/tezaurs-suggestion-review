package lv.tezaurs.tip.example.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.corpus.service.CorpusService;
import lv.tezaurs.tip.example.dto.ExampleResponse;
import lv.tezaurs.tip.example.dto.UpdateExampleRequest;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.dto.SelectCorpusExamplesRequest;
import lv.tezaurs.tip.example.service.ExampleService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/usage")
@RequiredArgsConstructor
public class ExampleController {
    private final ExampleService exampleService;
    private final CorpusService corpusService;

    @GetMapping("/examples")
    List<ExampleResponse> getExamplesForReview(@PathVariable UUID reviewId) {
        return exampleService.getExamplesForReview(reviewId);
    }

    @PostMapping("/examples")
    ExampleResponse saveExample(@PathVariable UUID reviewId, @Valid @RequestBody SaveExampleRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return exampleService.saveExample(reviewId, request, authorization);
    }

    @PutMapping("/examples/{exampleId}")
    ExampleResponse updateExample(@PathVariable UUID reviewId, @PathVariable UUID exampleId,
            @Valid @RequestBody UpdateExampleRequest request) {
        return exampleService.updateExample(reviewId, exampleId, request);
    }

    @DeleteMapping("/examples/{exampleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExample(@PathVariable UUID reviewId, @PathVariable UUID exampleId) {
        exampleService.deleteExample(reviewId, exampleId);
    }

    @DeleteMapping("/examples")
    List<ExampleResponse> deleteCorpusExamples(@PathVariable UUID reviewId, @RequestParam Corpus corpus) {
        return exampleService.deleteUnapprovedCorpusExamples(reviewId, corpus);
    }

    @PostMapping("/examples/select")
    List<ExampleResponse> selectCorpusExamples(@PathVariable UUID reviewId,
            @Valid @RequestBody SelectCorpusExamplesRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return corpusService.selectAndSaveCorpusExamples(reviewId, request, authorization);
    }
}
