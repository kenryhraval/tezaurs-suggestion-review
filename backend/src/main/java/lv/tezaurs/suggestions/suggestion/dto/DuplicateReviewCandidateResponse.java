package lv.tezaurs.suggestions.suggestion.dto;

import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;

public record DuplicateReviewCandidateResponse(
        UUID id,
        String reviewedTerm,
        ReviewStatus status,
        long sourceCount) {

    public static DuplicateReviewCandidateResponse from(ReviewCase review, long sourceCount) {
        return new DuplicateReviewCandidateResponse(
                review.getId(), review.getReviewedTerm(), review.getStatus(), sourceCount);
    }
}
