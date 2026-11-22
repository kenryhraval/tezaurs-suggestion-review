package lv.tezaurs.tip.example.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.example.entity.Example;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExampleRepository extends JpaRepository<Example, UUID> {
    @EntityGraph(attributePaths = "sense")
    @Query("""
            SELECT example FROM Example example LEFT JOIN example.sense sense
            WHERE example.reviewId = :reviewId
              AND (sense IS NULL OR sense.targetEntryId = :entryId
                   OR (:entryId IS NULL AND sense.targetEntryId IS NULL))
            ORDER BY example.createdAt, example.id
            """)
    List<Example> findForReview(UUID reviewId, Integer entryId);

    @Query("SELECT example.tokenNumber FROM Example example WHERE example.reviewId = :reviewId AND example.corpus = :corpus")
    List<Long> findSelectedTokenNumbers(UUID reviewId, Corpus corpus);
    Optional<Example> findByIdAndReviewId(UUID id, UUID reviewId);
    Optional<Example> findByReviewIdAndCorpusAndTokenNumber(UUID reviewId, Corpus corpus, Long tokenNumber);
}
