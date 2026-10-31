package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import lv.tezaurs.suggestions.suggestion.dto.ReviewCaseListItem;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface ReviewCaseRepository extends JpaRepository<ReviewCase, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReviewCase> findLockedById(UUID id);

    @Query("""
            SELECT review FROM ReviewCase review
            WHERE (:excludedCaseId IS NULL OR review.id <> :excludedCaseId)
              AND review.status <> :excludedStatus
              AND LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
            """)
    List<ReviewCase> findDuplicateCandidates(
            @Param("excludedCaseId") UUID excludedCaseId,
            @Param("excludedStatus") SuggestionStatus excludedStatus,
            @Param("query") String query, Pageable pageable);

    @Query("""
            SELECT review.id AS id,
                   review.reviewedTerm AS reviewedTerm,
                   review.status AS status,
                   review.createdAt AS createdAt,
                   SIZE(review.sources) AS sourceCount
            FROM ReviewCase review
            WHERE review.status = :status
              AND (:query = ''
                   OR LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
                   OR EXISTS (
                       SELECT link.sourceSuggestionId
                       FROM CaseSuggestion link
                       WHERE link.reviewCase = review
                         AND (LOWER(link.sourceSuggestion.lemma) LIKE CONCAT('%', LOWER(:query), '%')
                              OR LOWER(link.sourceSuggestion.gloss) LIKE CONCAT('%', LOWER(:query), '%'))
                   ))
            """)
    Page<ReviewCaseListItem> findForReviewQueue(
            @Param("status") SuggestionStatus status,
            @Param("query") String query,
            Pageable pageable);
}
