package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CorpusExample;

public record CorpusExampleResponse(
        UUID id,
        UUID suggestionId,
        String url,
        Instant createdAt) {

    public static CorpusExampleResponse from(CorpusExample example) {
        return new CorpusExampleResponse(example.getId(), example.getSuggestionId(),
                example.getUrl(), example.getCreatedAt());
    }
}
