package lv.tezaurs.suggestions.entry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record SaveLexemeDraftRequest(
        @NotBlank @Size(max = 255) String lemma,
        boolean primary,
        @NotNull Integer roleId,
        Integer paradigmId,
        @Size(max = 10000) String stem1,
        @Size(max = 10000) String stem2,
        @Size(max = 10000) String stem3,
        @NotNull List<@Size(max = 255) String> pronunciations,
        @NotNull Map<String, Object> flags,
        Map<String, Object> structuralRestrictions) {
}
