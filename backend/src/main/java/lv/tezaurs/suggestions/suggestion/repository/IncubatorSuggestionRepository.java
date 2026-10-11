package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionListItem;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IncubatorSuggestionRepository extends JpaRepository<IncubatorSuggestion, Integer> {
    @Query(value = """
            SELECT source.id,
                   source.lemma AS "submittedTerm",
                   source.gloss AS "submittedDefinition",
                   source.example AS "usageExample",
                   source.source,
                   source.flag_info AS "flagInfo",
                   source.notes,
                   source.contact,
                   COALESCE(review.status, 'NEW') AS status,
                   source.created_at AS "createdAt"
            FROM incubator.suggestions source
            LEFT JOIN review.suggestions review
                ON review.source_suggestion_id = source.id
            ORDER BY source.created_at, source.id
            """, nativeQuery = true)
    List<SuggestionListItem> findAllForReviewQueue();
}
