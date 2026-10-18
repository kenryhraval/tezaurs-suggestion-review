package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionListItem;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewSuggestionRepository extends JpaRepository<ReviewSuggestion, UUID> {
    List<ReviewSuggestion> findByReviewedTermContainingIgnoreCase(String query, Pageable pageable);

    @Query("""
            SELECT source.id AS id,
                   source.lemma AS submittedTerm,
                   source.gloss AS submittedDefinition,
                   source.example AS usageExample,
                   source.source AS source,
                   source.flagInfo AS flagInfo,
                   source.notes AS notes,
                   source.contact AS contact,
                   review.status AS status,
                   source.createdAt AS createdAt,
                   SIZE(review.sources) AS sourceCount
            FROM ReviewSuggestion review
            JOIN review.sources link
            JOIN link.sourceSuggestion source
            WHERE link.primarySource = true
              AND review.status = :status
              AND (:query = ''
                   OR LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
                   OR LOWER(source.lemma) LIKE CONCAT('%', LOWER(:query), '%')
                   OR LOWER(source.gloss) LIKE CONCAT('%', LOWER(:query), '%'))
            """)
    Page<SuggestionListItem> findForReviewQueue(
            @Param("status") SuggestionStatus status,
            @Param("query") String query,
            Pageable pageable);
}
