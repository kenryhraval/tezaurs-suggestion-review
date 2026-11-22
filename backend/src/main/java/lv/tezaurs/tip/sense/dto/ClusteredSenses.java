package lv.tezaurs.tip.sense.dto;

import java.util.List;

public record ClusteredSenses(List<Group> groups) {
    public record Group(String description, Integer senseId, List<Integer> exampleIds,
            List<Subsense> subsenses) {}
    public record Subsense(String description, Integer senseId, List<Integer> exampleIds) {}
}
