package lv.tezaurs.suggestions.usage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lv.tezaurs.suggestions.usage.entity.EvidenceSource;

public record SaveEvidenceRequest(
        @NotNull EvidenceSource source,
        Corpus corpus,
        @PositiveOrZero Long tokenNumber,
        @NotBlank @Size(max = 255) String query,
        @NotBlank @Size(max = 10000) String citation,
        @NotBlank @Size(max = 4000) String url,
        @Size(max = 4000) String reference,
        @Size(max = 4000) String note) {
}
