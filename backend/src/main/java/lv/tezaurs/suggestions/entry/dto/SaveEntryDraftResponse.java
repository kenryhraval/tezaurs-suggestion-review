package lv.tezaurs.suggestions.entry.dto;

import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;

public record SaveEntryDraftResponse(
        EntryDraftResponse draft,
        ReviewResponse review) {
}
