package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;

public record ReviewResponse(
        UUID id,
        Integer sourceSuggestionId,
        String reviewedTerm,
        TezaursStatus tezaursStatus,
        Long matchedEntryId,
        CheckStatus corpusStatus,
        boolean canComplete,
        CompletionBlockReason completionBlockReason,
        Instant createdAt,
        Instant updatedAt) {

    public static ReviewResponse from(Suggestion review) {
        return new ReviewResponse(review.getId(), review.getSourceSuggestionId(), review.getReviewedTerm(),
                review.getTezaursStatus(), review.getMatchedEntryId(), review.getCorpusStatus(),
                review.canComplete(), review.completionBlockReason(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
