package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;

public record RecordCorpusCheckRequest(@NotNull CheckStatus status) {
}
