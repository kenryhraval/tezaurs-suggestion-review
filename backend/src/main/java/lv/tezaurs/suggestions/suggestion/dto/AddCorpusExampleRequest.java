package lv.tezaurs.suggestions.suggestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddCorpusExampleRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "https?://\\S+", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "must be an HTTP or HTTPS URL")
        String url) {

    public AddCorpusExampleRequest {
        if (url != null) {
            url = url.trim();
        }
    }
}
