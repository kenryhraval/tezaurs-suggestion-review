package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;

public record ReviewListItemResponse(
        UUID id,
        String publicId,
        String reviewedTerm,
        ReviewStatus status,
        Instant createdAt,
        long sourceCount) {

    public static ReviewListItemResponse from(ReviewCase review) {
        return new ReviewListItemResponse(review.getId(), review.getPublicId(), review.getReviewedTerm(),
                review.getStatus(), review.getCreatedAt(), review.getSources().size());
    }
}
