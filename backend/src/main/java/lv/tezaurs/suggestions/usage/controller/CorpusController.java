package lv.tezaurs.suggestions.usage.controller;

import java.util.Arrays;
import java.util.List;
import lv.tezaurs.suggestions.usage.dto.Corpus;
import lv.tezaurs.suggestions.usage.dto.CorpusOption;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/corpora")
public class CorpusController {
    @GetMapping
    List<CorpusOption> list() {
        return Arrays.stream(Corpus.values())
                .map(corpus -> new CorpusOption(corpus, corpus.label))
                .toList();
    }
}
