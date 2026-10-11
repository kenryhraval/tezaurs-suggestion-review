package lv.tezaurs.suggestions.tezaurs.dto;

public record TezaursSenseResponse(
        Integer id,
        Integer parentSenseId,
        String gloss,
        Integer orderNo) {
}
