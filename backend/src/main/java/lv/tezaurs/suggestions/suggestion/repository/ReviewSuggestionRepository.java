package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionListItem;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewSuggestionRepository extends JpaRepository<ReviewSuggestion, UUID> {
    List<ReviewSuggestion> findByReviewedTermContainingIgnoreCase(String query, Pageable pageable);

    @Query(value = """
            SELECT source.id,
                   source.lemma AS "submittedTerm",
                   source.gloss AS "submittedDefinition",
                   source.example AS "usageExample",
                   source.source,
                   source.flag_info AS "flagInfo",
                   source.notes,
                   source.contact,
                   review.status,
                   source.created_at AS "createdAt"
            FROM review.suggestions review
            JOIN review.suggestion_sources link
                ON link.suggestion_id = review.id AND link.primary_source
            JOIN incubator.suggestions source
                ON source.id = link.source_suggestion_id
            WHERE review.status <> 'NEW'
              AND (CAST(:status AS VARCHAR) IS NULL OR review.status = :status)
            ORDER BY source.created_at, source.id
            """, nativeQuery = true)
    List<SuggestionListItem> findForReviewQueue(@Param("status") String status);
}
