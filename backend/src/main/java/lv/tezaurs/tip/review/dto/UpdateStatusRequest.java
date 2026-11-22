package lv.tezaurs.tip.review.dto;

import jakarta.validation.constraints.NotNull;
import lv.tezaurs.tip.review.entity.ReviewStatus;

public record UpdateStatusRequest(@NotNull ReviewStatus status) {
}
