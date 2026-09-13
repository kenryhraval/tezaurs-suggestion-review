package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSuggestionRequest(
        @NotBlank @Size(max = 255) String term,
        @NotBlank @Size(max = 10_000) String definition,
        @Size(max = 10_000) String usageExample,
        @Size(max = 10_000) String notes,
        @Size(max = 255) String submitterName,
        @Email @Size(max = 320) String submitterEmail) {
}
