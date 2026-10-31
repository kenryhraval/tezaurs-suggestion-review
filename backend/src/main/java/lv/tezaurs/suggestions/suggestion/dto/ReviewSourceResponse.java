package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestionSource;

public record ReviewSourceResponse(
        Integer id,
        String submittedTerm,
        String submittedDefinition,
        String usageExample,
        String source,
        String flagInfo,
        String notes,
        String contact,
        Instant createdAt) {

    public static ReviewSourceResponse from(ReviewSuggestionSource link) {
        IncubatorSuggestion source = link.getSourceSuggestion();
        return new ReviewSourceResponse(
                source.getId(), source.getLemma(), source.getGloss(), source.getExample(),
                source.getSource(), source.getFlagInfo(), source.getNotes(), source.getContact(),
                source.getCreatedAt());
    }
}
