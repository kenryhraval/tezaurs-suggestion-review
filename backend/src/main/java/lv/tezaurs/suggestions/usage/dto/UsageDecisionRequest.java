package lv.tezaurs.suggestions.usage.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;

public record UsageDecisionRequest(@NotNull UsageDecision decision, @Size(max = 10000) String note) {
}
