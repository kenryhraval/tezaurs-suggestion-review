package lv.tezaurs.tip.sense.dto;

import java.util.List;
import java.util.UUID;

public record SenseGroup(String description, UUID senseId, List<UUID> exampleIds,
        List<Subsense> subsenses) {
    public record Subsense(String description, UUID senseId, List<UUID> exampleIds) {}
}
