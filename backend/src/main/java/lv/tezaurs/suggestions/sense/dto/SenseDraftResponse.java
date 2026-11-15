package lv.tezaurs.suggestions.sense.dto;

import java.util.Map;
import java.util.UUID;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;

public record SenseDraftResponse(
        UUID id,
        String gloss,
        UUID parentDraftSenseId,
        Integer parentTezaursSenseId,
        int orderNo,
        Map<String, Object> flags,
        Map<String, Object> structuralRestrictions) {

    public static SenseDraftResponse from(ReviewSense sense) {
        return new SenseDraftResponse(sense.getId(), sense.getGloss(),
                sense.getParentDraftSenseId(), sense.getParentTezaursSenseId(),
                sense.getOrderNo(), sense.getFlags(), sense.getStructuralRestrictions());
    }
}
