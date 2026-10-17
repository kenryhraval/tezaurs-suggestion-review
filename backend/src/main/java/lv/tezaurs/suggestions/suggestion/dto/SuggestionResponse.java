package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;

public record SuggestionResponse(
        Integer id,
        String submittedTerm,
        String submittedDefinition,
        String usageExample,
        String source,
        String flagInfo,
        String notes,
        String contact,
        SuggestionStatus status,
        Instant createdAt) {

    public static SuggestionResponse from(SuggestionListItem item) {
        return new SuggestionResponse(item.getId(), item.getSubmittedTerm(), item.getSubmittedDefinition(),
                item.getUsageExample(), item.getSource(), item.getFlagInfo(), item.getNotes(),
                item.getContact(), SuggestionStatus.valueOf(item.getStatus()), item.getCreatedAt());
    }

    public static SuggestionResponse fromReview(IncubatorSuggestion sourceSuggestion, ReviewSuggestion review) {
        return from(sourceSuggestion, review.getStatus());
    }

    public static SuggestionResponse fromNewSuggestion(IncubatorSuggestion sourceSuggestion) {
        return from(sourceSuggestion, SuggestionStatus.NEW);
    }

    private static SuggestionResponse from(IncubatorSuggestion sourceSuggestion,
                                           SuggestionStatus status) {
        return new SuggestionResponse(sourceSuggestion.getId(), sourceSuggestion.getLemma(),
                sourceSuggestion.getGloss(), sourceSuggestion.getExample(), sourceSuggestion.getSource(),
                sourceSuggestion.getFlagInfo(), sourceSuggestion.getNotes(), sourceSuggestion.getContact(),
                status, sourceSuggestion.getCreatedAt());
    }
}
