package lv.tezaurs.tip.suggestion.repository;

import lv.tezaurs.tip.suggestion.entity.IncubatorSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncubatorSuggestionRepository extends JpaRepository<IncubatorSuggestion, Integer> {
}
