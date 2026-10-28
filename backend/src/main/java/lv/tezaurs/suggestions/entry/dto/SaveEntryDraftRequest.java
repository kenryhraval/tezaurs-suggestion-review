package lv.tezaurs.suggestions.entry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SaveEntryDraftRequest(
        @NotBlank @Size(max = 255) String heading,
        @NotNull Integer typeId,
        @Size(max = 10000) String etymology,
        @Size(max = 10000) String normativeComment,
        @Size(max = 10000) String scientificName,
        @Size(max = 10000) String source,
        @Size(max = 10000) String notes,
        @NotNull Map<String, Object> flags) {
}
