package lv.tezaurs.suggestions.meaning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMeaningRevisionRequest(@NotBlank @Size(max = 10_000) String gloss) {
}
