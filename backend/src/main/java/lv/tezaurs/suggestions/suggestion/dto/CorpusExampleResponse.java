package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CorpusExample;

public record CorpusExampleResponse(
        UUID id,
        UUID reviewId,
        String url,
        Instant createdAt) {

    public static CorpusExampleResponse from(CorpusExample example) {
        return new CorpusExampleResponse(example.getId(), example.getReviewId(),
                example.getUrl(), example.getCreatedAt());
    }
}
