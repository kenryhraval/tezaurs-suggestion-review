package lv.tezaurs.suggestions.suggestion.dto;

import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;

public record DuplicateReviewCandidateResponse(
        UUID id,
        String reviewedTerm,
        SuggestionStatus status,
        long sourceCount) {

    public static DuplicateReviewCandidateResponse from(ReviewCase review, long sourceCount) {
        return new DuplicateReviewCandidateResponse(
                review.getId(), review.getReviewedTerm(), review.getStatus(), sourceCount);
    }
}
