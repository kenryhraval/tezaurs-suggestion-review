package lv.tezaurs.tip.sense.dto;

import java.util.List;
import java.util.UUID;

public record SenseGroup(String description, Integer existingSenseId, List<UUID> exampleIds) {}
