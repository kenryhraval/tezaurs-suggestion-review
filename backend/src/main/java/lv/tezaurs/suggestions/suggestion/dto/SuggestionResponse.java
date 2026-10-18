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
        Instant createdAt,
        int sourceCount) {

    public static SuggestionResponse from(SuggestionListItem item) {
        return new SuggestionResponse(item.getId(), item.getSubmittedTerm(), item.getSubmittedDefinition(),
                item.getUsageExample(), item.getSource(), item.getFlagInfo(), item.getNotes(),
                item.getContact(), item.getStatus(), item.getCreatedAt(), item.getSourceCount());
    }

    public static SuggestionResponse fromReview(IncubatorSuggestion sourceSuggestion, ReviewSuggestion review) {
        return from(sourceSuggestion, review.getStatus(), review.getSources().size());
    }

    public static SuggestionResponse fromNewSuggestion(IncubatorSuggestion sourceSuggestion) {
        return from(sourceSuggestion, SuggestionStatus.NEW, 1);
    }

    private static SuggestionResponse from(IncubatorSuggestion sourceSuggestion,
                                           SuggestionStatus status,
                                           int sourceCount) {
        return new SuggestionResponse(sourceSuggestion.getId(), sourceSuggestion.getLemma(),
                sourceSuggestion.getGloss(), sourceSuggestion.getExample(), sourceSuggestion.getSource(),
                sourceSuggestion.getFlagInfo(), sourceSuggestion.getNotes(), sourceSuggestion.getContact(),
                status, sourceSuggestion.getCreatedAt(), sourceCount);
    }
}
