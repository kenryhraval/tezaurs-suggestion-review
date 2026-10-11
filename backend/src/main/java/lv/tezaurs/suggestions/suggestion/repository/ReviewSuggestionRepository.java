package lv.tezaurs.suggestions.suggestion.repository;

import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSuggestionRepository extends JpaRepository<ReviewSuggestion, UUID> {
    Optional<ReviewSuggestion> findBySourceSuggestionId(Integer sourceSuggestionId);
}
