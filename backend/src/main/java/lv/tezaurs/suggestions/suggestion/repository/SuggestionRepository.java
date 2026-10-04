package lv.tezaurs.suggestions.suggestion.repository;

import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<Suggestion, UUID> {
    Optional<Suggestion> findBySourceSuggestionId(Integer sourceSuggestionId);
}
