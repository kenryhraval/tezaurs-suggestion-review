package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;

public interface SuggestionListItem {
    Integer getId();

    String getSubmittedTerm();

    String getSubmittedDefinition();

    String getUsageExample();

    String getSource();

    String getFlagInfo();

    String getNotes();

    String getContact();

    SuggestionStatus getStatus();

    Instant getCreatedAt();

    Integer getSourceCount();
}
