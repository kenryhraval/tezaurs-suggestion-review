package lv.tezaurs.tip.sense.dto;

import jakarta.validation.constraints.Size;

public record ClusterSensesRequest(@Size(max = 100) String modelId, @Size(max = 4000) String additionalInstructions) {
}
