package lv.tezaurs.suggestions.suggestion.repository;

import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestionSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSuggestionSourceRepository extends JpaRepository<ReviewSuggestionSource, Integer> {
    long countByReviewId(UUID reviewId);
}
