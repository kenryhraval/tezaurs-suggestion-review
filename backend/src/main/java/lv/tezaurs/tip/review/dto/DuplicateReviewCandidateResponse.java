package lv.tezaurs.tip.review.dto;

import java.util.UUID;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;

public record DuplicateReviewCandidateResponse(
        UUID id,
        String reviewedTerm,
        ReviewStatus status,
        long sourceCount) {

    public static DuplicateReviewCandidateResponse from(Review review, long sourceCount) {
        return new DuplicateReviewCandidateResponse(
                review.getId(), review.getReviewedTerm(), review.getStatus(), sourceCount);
    }
}
