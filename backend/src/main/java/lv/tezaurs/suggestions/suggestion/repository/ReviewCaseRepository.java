package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.dto.ReviewCaseListItem;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewCaseRepository extends JpaRepository<ReviewCase, UUID> {
    List<ReviewCase> findByReviewedTermContainingIgnoreCase(String query, Pageable pageable);

    @Query(value = """
            SELECT review.id AS id,
                   review.reviewedTerm AS reviewedTerm,
                   review.status AS status,
                   review.createdAt AS createdAt,
                   COUNT(source) AS sourceCount
            FROM ReviewCase review
            JOIN review.sources link
            JOIN link.sourceSuggestion source
            WHERE review.status = :status
              AND (:query = ''
                   OR LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
                   OR LOWER(source.lemma) LIKE CONCAT('%', LOWER(:query), '%')
                   OR LOWER(source.gloss) LIKE CONCAT('%', LOWER(:query), '%'))
            GROUP BY review.id, review.reviewedTerm, review.status, review.createdAt
            """,
            countQuery = """
                    SELECT COUNT(DISTINCT review)
                    FROM ReviewCase review
                    JOIN review.sources link
                    JOIN link.sourceSuggestion source
                    WHERE review.status = :status
                      AND (:query = ''
                           OR LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
                           OR LOWER(source.lemma) LIKE CONCAT('%', LOWER(:query), '%')
                           OR LOWER(source.gloss) LIKE CONCAT('%', LOWER(:query), '%'))
                    """)
    Page<ReviewCaseListItem> findForReviewQueue(
            @Param("status") SuggestionStatus status,
            @Param("query") String query,
            Pageable pageable);
}
