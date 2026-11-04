package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;

public record UpdateStatusRequest(@NotNull ReviewStatus status) {
}
