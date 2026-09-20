package lv.tezaurs.suggestions.suggestion.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;

public record SuggestionResponse(
        UUID id,
        String submittedTerm,
        String reviewedTerm,
        String usageExample,
        String notes,
        String submitterName,
        String submitterEmail,
        SuggestionStatus status,
        TezaursStatus tezaursStatus,
        Long matchedEntryId,
        CheckStatus corpusStatus,
        boolean canComplete,
        CompletionBlockReason completionBlockReason,
        Instant createdAt,
        Instant updatedAt) {

    public static SuggestionResponse from(Suggestion suggestion) {
        return new SuggestionResponse(suggestion.getId(), suggestion.getSubmittedTerm(),
                suggestion.getReviewedTerm(), suggestion.getUsageExample(), suggestion.getNotes(),
                suggestion.getSubmitterName(), suggestion.getSubmitterEmail(),
                suggestion.getStatus(), suggestion.getTezaursStatus(), suggestion.getMatchedEntryId(),
                suggestion.getCorpusStatus(), suggestion.canComplete(), suggestion.completionBlockReason(),
                suggestion.getCreatedAt(), suggestion.getUpdatedAt());
    }
}
