package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCorpusExample;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCorpusExampleRepository extends JpaRepository<ReviewCorpusExample, UUID> {
    List<ReviewCorpusExample> findAllByReviewIdOrderByCreatedAtAscIdAsc(UUID reviewId);

    boolean existsByReviewIdAndUrl(UUID reviewId, String url);
}
