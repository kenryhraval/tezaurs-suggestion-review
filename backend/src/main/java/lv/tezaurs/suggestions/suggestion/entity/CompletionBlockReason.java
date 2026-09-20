package lv.tezaurs.suggestions.suggestion.entity;

/** The next required step before a suggestion may be completed manually. */
public enum CompletionBlockReason {
    TEZAURS_CHECK_REQUIRED,
    MEANING_CHECK_REQUIRED,
    CORPUS_CHECK_REQUIRED
}
