package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;

public record ReviewResponse(
        UUID id,
        String reviewedTerm,
        TezaursStatus tezaursStatus,
        Integer matchedEntryId,
        CheckStatus corpusStatus,
        boolean canComplete,
        CompletionBlockReason completionBlockReason,
        Instant createdAt,
        Instant updatedAt) {

    public static ReviewResponse from(ReviewSuggestion review) {
        return new ReviewResponse(review.getId(), review.getReviewedTerm(),
                review.getTezaursStatus(), review.getMatchedEntryId(), review.getCorpusStatus(),
                review.canComplete(), review.completionBlockReason(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
