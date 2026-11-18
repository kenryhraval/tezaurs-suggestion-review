package lv.tezaurs.suggestions.suggestion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface ReviewCaseRepository extends JpaRepository<ReviewCase, UUID> {
    Optional<ReviewCase> findByPublicId(String publicId);

    long countByStatusNotIn(List<ReviewStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReviewCase> findLockedById(UUID id);

    @Query("""
            SELECT review FROM ReviewCase review
            WHERE (:excludedCaseId IS NULL OR review.id <> :excludedCaseId)
              AND review.status NOT IN :excludedStatuses
              AND LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
            """)
    List<ReviewCase> findDuplicateCandidates(
            @Param("excludedCaseId") UUID excludedCaseId,
            @Param("excludedStatuses") List<ReviewStatus> excludedStatuses,
            @Param("query") String query, Pageable pageable);

    @Query("""
            SELECT review
            FROM ReviewCase review
            WHERE (:status IS NULL OR review.status = :status)
              AND review.type IN :types
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
    Page<ReviewCase> findForReviewQueue(
            @Param("status") ReviewStatus status,
            @Param("types") List<CaseType> types,
            @Param("query") String query,
            Pageable pageable);
}
