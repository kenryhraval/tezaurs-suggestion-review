package lv.tezaurs.tip.sense.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record GenerateDefinitionsRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Valid SenseInput> groups,
        @Size(max = 100) String modelId) {

    public record SenseInput(
            @NotNull UUID id,
            @NotBlank @Size(max = 2000) String description,
            @NotEmpty List<@NotNull UUID> exampleIds) {
    }
}
