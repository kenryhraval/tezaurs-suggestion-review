package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;

public interface SuggestionListItem {
    Integer getId();

    String getSubmittedTerm();

    String getSubmittedDefinition();

    String getUsageExample();

    String getSource();

    String getFlagInfo();

    String getNotes();

    String getContact();

    String getStatus();

    Instant getCreatedAt();
}
