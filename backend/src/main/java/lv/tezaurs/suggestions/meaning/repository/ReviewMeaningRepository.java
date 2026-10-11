package lv.tezaurs.suggestions.meaning.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.meaning.entity.ReviewMeaning;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewMeaningRepository extends JpaRepository<ReviewMeaning, UUID> {
    List<ReviewMeaning> findAllByReviewIdOrderByCreatedAtAscIdAsc(UUID reviewId);

    Optional<ReviewMeaning> findByIdAndReviewId(UUID id, UUID reviewId);
}
