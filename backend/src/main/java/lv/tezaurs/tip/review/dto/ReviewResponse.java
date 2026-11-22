package lv.tezaurs.tip.review.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;

@Builder
public record ReviewResponse(
        UUID id,
        String publicId,
        String reviewedTerm,
        ReviewStatus status,
        String lastStatusChangedBy,
        ReviewType type,
        Integer matchedEntryId,
        UsageDecision usageDecision,
        String usageNote,
        String usageDecidedBy,
        Instant usageDecidedAt,
        Instant createdAt,
        List<ReviewSuggestionResponse> sources) {

    public static ReviewResponse from(Review review) {
        List<ReviewSuggestionResponse> suggestions = review.getSuggestions().stream()
                .sorted(Comparator.comparingInt(link -> link.getSourceSuggestion().getId()))
                .map(ReviewSuggestionResponse::from)
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
                .createdAt(review.getCreatedAt())
                .sources(suggestions)
                .build();
    }
}
