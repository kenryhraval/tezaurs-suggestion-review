package lv.tezaurs.tip.review.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByPublicId(String publicId);

    long countByStatusNotIn(List<ReviewStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Review> findLockedById(UUID id);

    @Query("""
            SELECT review FROM Review review
            WHERE (:excludedReviewId IS NULL OR review.id <> :excludedReviewId)
              AND review.status NOT IN :excludedStatuses
              AND LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
            """)
    List<Review> findDuplicateCandidates(
            @Param("excludedReviewId") UUID excludedReviewId,
            @Param("excludedStatuses") List<ReviewStatus> excludedStatuses,
            @Param("query") String query, Pageable pageable);

    @Query("""
            SELECT review
            FROM Review review
            WHERE (:status IS NULL OR review.status = :status)
              AND review.type IN :types
              AND (:query = ''
                   OR LOWER(review.reviewedTerm) LIKE CONCAT('%', LOWER(:query), '%')
                   OR EXISTS (
                       SELECT link.sourceSuggestionId
                       FROM ReviewSuggestion link
                       WHERE link.review = review
                         AND (LOWER(link.sourceSuggestion.lemma) LIKE CONCAT('%', LOWER(:query), '%')
                              OR LOWER(link.sourceSuggestion.gloss) LIKE CONCAT('%', LOWER(:query), '%'))
                   ))
            """)
    Page<Review> findForReviewQueue(
            @Param("status") ReviewStatus status,
            @Param("types") List<ReviewType> types,
            @Param("query") String query,
            Pageable pageable);
}
