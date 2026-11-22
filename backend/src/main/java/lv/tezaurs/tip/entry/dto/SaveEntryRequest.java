package lv.tezaurs.tip.entry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lv.tezaurs.tip.entry.entity.EntryType;

public record SaveEntryRequest(
        @NotBlank @Size(max = 255) String heading,
        @NotNull EntryType type,
        @Size(max = 10000) String etymology,
        @Size(max = 10000) String normativeComment,
        @Size(max = 10000) String scientificName,
        @Size(max = 10000) String source,
        @Size(max = 10000) String notes,
        @NotNull Map<String, Object> flags) {
}
