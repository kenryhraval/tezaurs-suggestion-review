package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;

public record RecordTezaursCheckRequest(@NotNull TezaursStatus status, Long matchedEntryId) {
}
