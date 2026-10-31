package lv.tezaurs.suggestions.suggestion.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ReviewPageResponse(
        List<ReviewListItemResponse> items,
        int page,
        int totalPages,
        long totalItems) {

    public static ReviewPageResponse from(Page<ReviewListItemResponse> reviews) {
        return new ReviewPageResponse(
                reviews.getContent(), reviews.getNumber(),
                reviews.getTotalPages(), reviews.getTotalElements());
    }
}
