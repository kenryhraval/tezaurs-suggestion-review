package lv.tezaurs.tip.review.dto;

import java.time.Instant;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import lv.tezaurs.tip.suggestion.entity.IncubatorSuggestion;

public record ReviewSuggestionResponse(
        Integer id,
        String submittedTerm,
        String submittedDefinition,
        String usageExample,
        String source,
        String flagInfo,
        String notes,
        String contact,
        Instant createdAt) {

    public static ReviewSuggestionResponse from(ReviewSuggestion link) {
        IncubatorSuggestion source = link.getSourceSuggestion();
        return new ReviewSuggestionResponse(
                source.getId(), source.getLemma(), source.getGloss(), source.getExample(),
                source.getSource(), source.getFlagInfo(), source.getNotes(), source.getContact(),
                source.getCreatedAt());
    }
}
