package lv.tezaurs.suggestions.suggestion.entity;

public enum TezaursStatus {
    NOT_CHECKED,
    FOUND,
    MEANING_FOUND,
    MEANING_NOT_FOUND,
    NOT_FOUND;

    public boolean entryExists() {
        return this == FOUND || this == MEANING_FOUND || this == MEANING_NOT_FOUND;
    }
}
