package lv.tezaurs.suggestions.meaning.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.entity.MeaningStatus;

public record MeaningResponse(
        UUID id,
        UUID reviewId,
        UUID supersedesMeaningId,
        MeaningOrigin origin,
        String gloss,
        MeaningStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static MeaningResponse from(Meaning meaning) {
        return new MeaningResponse(meaning.getId(), meaning.getReviewId(),
                meaning.getSupersedesMeaningId(), meaning.getOrigin(), meaning.getGloss(), meaning.getStatus(),
                meaning.getCreatedAt(), meaning.getUpdatedAt());
    }
}
