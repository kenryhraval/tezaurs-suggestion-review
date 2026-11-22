package lv.tezaurs.tip.example.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.example.entity.Example;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExampleRepository extends JpaRepository<Example, UUID> {
    @EntityGraph(attributePaths = "sense")
    List<Example> findByReviewIdOrderByCreatedAtAscIdAsc(UUID reviewId);
    Optional<Example> findByIdAndReviewId(UUID id, UUID reviewId);
    Optional<Example> findByReviewIdAndCorpusAndTokenNumber(UUID reviewId, Corpus corpus, Long tokenNumber);
}
