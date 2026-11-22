package lv.tezaurs.tip.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lv.tezaurs.tip.review.entity.UsageDecision;

public record UsageDecisionRequest(@NotNull UsageDecision decision, @Size(max = 10000) String note) {
}
