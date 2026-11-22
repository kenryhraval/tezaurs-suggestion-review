package lv.tezaurs.tip.sense.dto;

import java.util.List;

public record SenseDefinitions(List<Definition> definitions) {
    public record Definition(Integer senseId, String definition) {}
}
