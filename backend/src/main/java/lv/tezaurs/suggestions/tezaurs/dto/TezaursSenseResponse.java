package lv.tezaurs.suggestions.tezaurs.dto;

import lv.tezaurs.suggestions.tezaurs.entity.TezaursSense;

public record TezaursSenseResponse(
        Integer id,
        Integer parentSenseId,
        String gloss,
        Integer orderNo) {

    public static TezaursSenseResponse from(TezaursSense sense) {
        return new TezaursSenseResponse(sense.getId(), sense.getParentSenseId(),
                sense.getGloss(), sense.getOrderNo());
    }
}
