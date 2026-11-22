package lv.tezaurs.tip.sense.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lv.tezaurs.tip.sense.entity.Sense;

public record SenseResponse(
        UUID id,
        String gloss,
        boolean approved,
        Integer existingSenseId,
        UUID parentSenseId,
        int orderNo,
        Map<String, Object> flags,
        List<UUID> exampleIds) {

    public static SenseResponse from(Sense sense) {
        return new SenseResponse(sense.getId(), sense.getGloss(), sense.isApproved(), sense.getExistingSenseId(),
                sense.getParentSenseId(),
                sense.getOrderNo(), sense.getFlags(),
                sense.getExamples().stream().map(example -> example.getId()).sorted().toList());
    }
}
