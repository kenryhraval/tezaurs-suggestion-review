package lv.tezaurs.tip.corpus.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.corpus.dto.CorpusOption;
import lv.tezaurs.tip.corpus.dto.CorpusSearchResponse;
import lv.tezaurs.tip.corpus.service.CorpusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/corpora")
@RequiredArgsConstructor
public class CorpusController {
    private final CorpusService corpusService;

    @GetMapping
    List<CorpusOption> list() {
        return Arrays.stream(Corpus.values())
                .map(corpus -> new CorpusOption(corpus, corpus.label))
                .toList();
    }

    @GetMapping("/{corpus}/search")
    CorpusSearchResponse search(@PathVariable Corpus corpus,
            @RequestParam @NotBlank @Size(max = 255) String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "false") boolean exactOnly) {
        return corpusService.searchExamples(query, corpus, page, exactOnly);
    }
}
