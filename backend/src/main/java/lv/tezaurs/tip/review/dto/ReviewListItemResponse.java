package lv.tezaurs.tip.review.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;

public record ReviewListItemResponse(
        UUID id,
        String publicId,
        String reviewedTerm,
        ReviewStatus status,
        Instant createdAt,
        long sourceCount) {

    public static ReviewListItemResponse from(Review review) {
        return new ReviewListItemResponse(review.getId(), review.getPublicId(), review.getReviewedTerm(),
                review.getStatus(), review.getCreatedAt(), review.getSuggestions().size());
    }
}
