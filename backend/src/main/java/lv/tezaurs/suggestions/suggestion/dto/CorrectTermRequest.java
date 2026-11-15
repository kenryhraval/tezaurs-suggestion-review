package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotBlank;

public record CorrectTermRequest(@NotBlank String reviewedTerm) {
}
