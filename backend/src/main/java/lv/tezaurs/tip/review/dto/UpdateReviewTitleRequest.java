package lv.tezaurs.tip.review.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateReviewTitleRequest(@NotBlank String reviewedTerm) {
}
