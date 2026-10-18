package lv.tezaurs.suggestions.suggestion.repository;

import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncubatorSuggestionRepository extends JpaRepository<IncubatorSuggestion, Integer> {
    @Query("""
            SELECT source
            FROM IncubatorSuggestion source
            LEFT JOIN source.reviewSource reviewSource
            WHERE reviewSource.sourceSuggestionId IS NULL
              AND (:query = ''
                   OR LOWER(source.lemma) LIKE CONCAT('%', LOWER(:query), '%')
                   OR LOWER(source.gloss) LIKE CONCAT('%', LOWER(:query), '%'))
            """)
    Page<IncubatorSuggestion> findUnstartedForReviewQueue(
            @Param("query") String query, Pageable pageable);
}
