package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;

@Builder
public record ReviewResponse(
        UUID id,
        String publicId,
        String reviewedTerm,
        ReviewStatus status,
        String lastStatusChangedBy,
        CaseType type,
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
        List<ReviewSourceResponse> sources = review.getSources().stream()
                .sorted(Comparator.comparingInt(link -> link.getSourceSuggestion().getId()))
                .map(ReviewSourceResponse::from)
                .toList();

        return ReviewResponse.builder()
                .id(review.getId())
                .publicId(review.getPublicId())
                .reviewedTerm(review.getReviewedTerm())
                .status(review.getStatus())
                .lastStatusChangedBy(review.getLastStatusChangedBy())
                .type(review.getType())
                .matchedEntryId(review.getMatchedEntryId())
                .usageDecision(review.getUsageDecision())
                .usageNote(review.getUsageNote())
                .usageDecidedBy(review.getUsageDecidedBy())
                .usageDecidedAt(review.getUsageDecidedAt())
                .canComplete(blockReason == null)
                .completionBlockReason(blockReason)
                .createdAt(review.getCreatedAt())
                .sources(sources)
                .build();
    }
}
