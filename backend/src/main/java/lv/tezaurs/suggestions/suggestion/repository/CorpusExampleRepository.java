package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CorpusExample;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorpusExampleRepository extends JpaRepository<CorpusExample, UUID> {
    List<CorpusExample> findAllByReviewIdOrderByCreatedAtAscIdAsc(UUID reviewId);

    boolean existsByReviewIdAndUrl(UUID reviewId, String url);
}
