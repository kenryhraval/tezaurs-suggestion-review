package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;

public record SuggestionResponse(
        Integer id,
        String submittedTerm,
        String submittedDefinition,
        String usageExample,
        String source,
        String flagInfo,
        String notes,
        String contact,
        Instant createdAt) {

    public static SuggestionResponse fromNewSuggestion(IncubatorSuggestion sourceSuggestion) {
        return new SuggestionResponse(sourceSuggestion.getId(), sourceSuggestion.getLemma(),
                sourceSuggestion.getGloss(), sourceSuggestion.getExample(), sourceSuggestion.getSource(),
                sourceSuggestion.getFlagInfo(), sourceSuggestion.getNotes(), sourceSuggestion.getContact(),
                sourceSuggestion.getCreatedAt());
    }
}
