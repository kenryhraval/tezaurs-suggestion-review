package lv.tezaurs.tip.entry.dto;

import lv.tezaurs.tip.review.dto.ReviewResponse;

public record SaveLexemeResponse(
        EntryResponse.LexemeResponse lexeme,
        ReviewResponse review) {
}
