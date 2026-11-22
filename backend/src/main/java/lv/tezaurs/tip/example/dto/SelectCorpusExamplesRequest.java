package lv.tezaurs.tip.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lv.tezaurs.tip.corpus.dto.Corpus;

public record SelectCorpusExamplesRequest(
        @NotNull Corpus corpus,
        @NotBlank @Size(max = 255) String query,
        boolean exactOnly,
        @Positive Integer count) {
}
