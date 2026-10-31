package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;

public interface ReviewCaseListItem {
    UUID getId();

    String getReviewedTerm();

    SuggestionStatus getStatus();

    Instant getCreatedAt();

    long getSourceCount();
}
