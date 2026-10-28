package lv.tezaurs.suggestions.entry.dto;

import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;

public record SaveLexemeDraftResponse(
        EntryDraftResponse.LexemeDraftResponse lexeme,
        ReviewResponse review) {
}
