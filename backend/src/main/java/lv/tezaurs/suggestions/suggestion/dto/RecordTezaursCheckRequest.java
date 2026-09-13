package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;

public record RecordTezaursCheckRequest(@NotNull CheckStatus status, Long matchedEntryId) {
}
