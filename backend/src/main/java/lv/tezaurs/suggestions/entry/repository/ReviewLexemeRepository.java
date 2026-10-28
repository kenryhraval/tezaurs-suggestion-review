package lv.tezaurs.suggestions.entry.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLexemeRepository extends JpaRepository<ReviewLexeme, UUID> {
    Optional<ReviewLexeme> findFirstByEntryIdOrderByOrderNo(UUID entryId);

    Optional<ReviewLexeme> findByIdAndEntryId(UUID id, UUID entryId);

    List<ReviewLexeme> findAllByEntryIdOrderByOrderNoAscIdAsc(UUID entryId);
}
