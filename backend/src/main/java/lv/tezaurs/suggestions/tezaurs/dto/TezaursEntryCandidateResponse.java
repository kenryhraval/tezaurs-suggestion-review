package lv.tezaurs.suggestions.tezaurs.dto;

import java.util.List;

public record TezaursEntryCandidateResponse(
        Integer id,
        String heading,
        String humanKey,
        String matchedLexeme,
        List<TezaursSenseResponse> senses) {
}
