package lv.tezaurs.tip.sense.dto;

import java.util.List;

public record ClusteredSenses(List<Group> groups) {
    public record Group(String description, Integer existingSenseId, List<Integer> exampleIds) {}
}
