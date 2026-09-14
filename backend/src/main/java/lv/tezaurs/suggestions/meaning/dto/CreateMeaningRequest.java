package lv.tezaurs.suggestions.meaning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateMeaningRequest(
        @NotBlank @Size(max = 10_000) String gloss,
        UUID parentMeaningId) {
}
