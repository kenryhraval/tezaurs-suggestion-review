package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;

public interface ReviewCaseListItem {
    UUID getId();

    String getReviewedTerm();

    ReviewStatus getStatus();

    Instant getCreatedAt();

    long getSourceCount();
}
