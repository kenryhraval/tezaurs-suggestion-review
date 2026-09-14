package lv.tezaurs.suggestions.meaning.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeaningRepository extends JpaRepository<Meaning, UUID> {
    List<Meaning> findAllBySuggestionIdOrderByCreatedAtAscIdAsc(UUID suggestionId);

    Optional<Meaning> findByIdAndSuggestionId(UUID id, UUID suggestionId);
}
