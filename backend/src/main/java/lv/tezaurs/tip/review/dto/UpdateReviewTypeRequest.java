package lv.tezaurs.tip.review.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.tip.review.entity.ReviewType;

public record UpdateReviewTypeRequest(
        @NotNull ReviewType type, Integer matchedEntryId, Boolean meaningAlreadyExists) {
}
