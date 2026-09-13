package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;

public record UpdateStatusRequest(@NotNull SuggestionStatus status) {
}
