package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncubatorSuggestionRepository extends JpaRepository<IncubatorSuggestion, Integer> {
    List<IncubatorSuggestion> findAllByReviewSourceIsNullOrderByCreatedAtAscIdAsc();
}
