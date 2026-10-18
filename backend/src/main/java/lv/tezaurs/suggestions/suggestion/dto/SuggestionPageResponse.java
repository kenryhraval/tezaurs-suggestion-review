package lv.tezaurs.suggestions.suggestion.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record SuggestionPageResponse(
        List<SuggestionResponse> items,
        int page,
        int totalPages,
        long totalItems) {

    public static SuggestionPageResponse from(Page<SuggestionResponse> suggestions) {
        return new SuggestionPageResponse(
                suggestions.getContent(),
                suggestions.getNumber(),
                suggestions.getTotalPages(),
                suggestions.getTotalElements());
    }
}
