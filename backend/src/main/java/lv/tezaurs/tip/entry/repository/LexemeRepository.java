package lv.tezaurs.tip.entry.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.entry.entity.Lexeme;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LexemeRepository extends JpaRepository<Lexeme, UUID> {
    Optional<Lexeme> findByIdAndEntryId(UUID id, UUID entryId);

    List<Lexeme> findAllByEntryIdOrderByOrderNoAscIdAsc(UUID entryId);
}
