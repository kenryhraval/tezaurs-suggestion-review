package lv.tezaurs.tip.example.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateExampleRequest(UUID senseId, @NotNull Boolean approved) {}
