package lv.tezaurs.suggestions.sense.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SaveSenseDraftsRequest(@NotEmpty List<@Valid SenseDraftRequest> senses) {
    public record SenseDraftRequest(
            UUID id,
            @NotBlank @Size(max = 10000) String gloss,
            UUID parentDraftSenseId,
            Integer parentTezaursSenseId,
            @NotNull Map<String, Object> flags,
            Map<String, Object> structuralRestrictions) {
    }
}
