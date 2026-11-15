package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;

public record UpdateCaseTypeRequest(
        @NotNull CaseType type, Integer matchedEntryId, Boolean meaningAlreadyExists) {
}
