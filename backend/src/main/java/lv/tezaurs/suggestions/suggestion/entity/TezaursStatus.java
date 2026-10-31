package lv.tezaurs.suggestions.suggestion.entity;

public enum TezaursStatus {
    NOT_CHECKED,
    FOUND,
    MEANING_ALREADY_EXISTS,
    MEANING_NEEDS_EDITING,
    MEANING_NOT_FOUND,
    NOT_FOUND;

    public boolean entryExists() {
        return this == FOUND || this == MEANING_ALREADY_EXISTS || this == MEANING_NOT_FOUND || this == MEANING_NEEDS_EDITING;
    }
}
