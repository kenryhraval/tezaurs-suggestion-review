package lv.tezaurs.suggestions.meaning.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeaningRepository extends JpaRepository<Meaning, UUID> {
    List<Meaning> findAllByReviewIdOrderByCreatedAtAscIdAsc(UUID reviewId);

    Optional<Meaning> findByIdAndReviewId(UUID id, UUID reviewId);
}
