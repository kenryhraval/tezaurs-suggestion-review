package lv.tezaurs.suggestions.entry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateEntryDraftRequest(
        @Valid @NotNull SaveEntryDraftRequest entry,
        @Valid @NotNull SaveLexemeDraftRequest primaryLexeme) {
}
