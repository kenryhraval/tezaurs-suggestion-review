package lv.tezaurs.tip.sense.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.sense.entity.Sense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenseRepository extends JpaRepository<Sense, UUID> {
    List<Sense> findAllByReviewId(UUID reviewId);
    Optional<Sense> findByIdAndReviewId(UUID id, UUID reviewId);
    List<Sense> findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(UUID reviewId, Integer targetEntryId);
}
