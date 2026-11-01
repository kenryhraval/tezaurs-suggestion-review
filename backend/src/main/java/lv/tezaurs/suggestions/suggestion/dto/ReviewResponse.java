package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;

public record ReviewResponse(
        UUID id,
        String reviewedTerm,
        SuggestionStatus status,
        TezaursStatus tezaursStatus,
        Integer matchedEntryId,
        UsageDecision usageDecision,
        String usageNote,
        String usageDecidedBy,
        Instant usageDecidedAt,
        boolean canComplete,
        CompletionBlockReason completionBlockReason,
        Instant createdAt,
        List<ReviewSourceResponse> sources) {

    public static ReviewResponse from(ReviewCase review, CompletionBlockReason blockReason) {
        return new ReviewResponse(review.getId(), review.getReviewedTerm(), review.getStatus(),
                review.getTezaursStatus(), review.getMatchedEntryId(),
                review.getUsageDecision(), review.getUsageNote(), review.getUsageDecidedBy(), review.getUsageDecidedAt(),
                blockReason == null, blockReason, review.getCreatedAt(),
                review.getSources().stream()
                        .sorted(Comparator.comparingInt(link -> link.getSourceSuggestion().getId()))
                        .map(ReviewSourceResponse::from)
                        .toList());
    }
}
