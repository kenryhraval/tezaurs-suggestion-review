package lv.tezaurs.suggestions.suggestion.repository;

import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.CaseSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseSuggestionRepository extends JpaRepository<CaseSuggestion, Integer> {
    long countByReviewCaseId(UUID caseId);
}
