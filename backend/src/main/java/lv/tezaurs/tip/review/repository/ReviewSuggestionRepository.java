package lv.tezaurs.tip.review.repository;

import java.util.UUID;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSuggestionRepository extends JpaRepository<ReviewSuggestion, Integer> {
    long countByReviewId(UUID reviewId);
}
