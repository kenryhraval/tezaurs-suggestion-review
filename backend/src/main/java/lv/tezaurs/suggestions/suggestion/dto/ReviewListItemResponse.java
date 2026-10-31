package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;

public record ReviewListItemResponse(
        UUID id,
        String reviewedTerm,
        SuggestionStatus status,
        Instant createdAt,
        long sourceCount) {

    public static ReviewListItemResponse from(ReviewSuggestionListItem item) {
        return new ReviewListItemResponse(item.getId(), item.getReviewedTerm(),
                item.getStatus(), item.getCreatedAt(), item.getSourceCount());
    }
}
