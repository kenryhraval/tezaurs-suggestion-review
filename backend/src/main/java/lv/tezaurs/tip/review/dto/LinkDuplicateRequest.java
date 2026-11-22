package lv.tezaurs.tip.review.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkDuplicateRequest(@NotNull UUID reviewId) {
}
