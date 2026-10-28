package lv.tezaurs.suggestions.sense.repository;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSenseRepository extends JpaRepository<ReviewSense, UUID> {
    List<ReviewSense> findAllBySuggestionIdOrderByOrderNoAscIdAsc(UUID suggestionId);
}
