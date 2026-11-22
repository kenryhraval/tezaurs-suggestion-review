package lv.tezaurs.tip.sense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record SaveSenseRequest(
        @NotBlank @Size(max = 10000) String gloss,
        UUID parentSenseId,
        Map<String, Object> flags,
        Boolean approved) {
}
