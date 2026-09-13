package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectTermRequest(@NotBlank @Size(max = 255) String reviewedTerm) {
}
