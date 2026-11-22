package lv.tezaurs.tip.sense.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MergeSenseRequest(@NotNull UUID targetSenseId) {}
