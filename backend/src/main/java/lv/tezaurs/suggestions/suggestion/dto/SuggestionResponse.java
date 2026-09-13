package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;

public record SuggestionResponse(
        UUID id,
        String submittedTerm,
        String reviewedTerm,
        String submittedDefinition,
        String usageExample,
        String notes,
        String submitterName,
        String submitterEmail,
        SuggestionStatus status,
        CheckStatus tezaursStatus,
        Long matchedEntryId,
        CheckStatus corpusStatus,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static SuggestionResponse from(Suggestion suggestion) {
        return new SuggestionResponse(suggestion.getId(), suggestion.getSubmittedTerm(),
                suggestion.getReviewedTerm(), suggestion.getSubmittedDefinition(), suggestion.getUsageExample(),
                suggestion.getNotes(), suggestion.getSubmitterName(), suggestion.getSubmitterEmail(),
                suggestion.getStatus(), suggestion.getTezaursStatus(), suggestion.getMatchedEntryId(),
                suggestion.getCorpusStatus(), suggestion.getVersion(), suggestion.getCreatedAt(),
                suggestion.getUpdatedAt());
    }
}
