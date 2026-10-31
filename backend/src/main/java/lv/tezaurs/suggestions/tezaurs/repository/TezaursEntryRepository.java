package lv.tezaurs.suggestions.tezaurs.repository;

import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TezaursEntryRepository extends JpaRepository<TezaursEntry, Integer> {

    Optional<TezaursEntry> findFirstByHeadingOrderByHomonymNoDesc(String heading);

    @Query("""
            SELECT entry
            FROM TezaursEntry entry
            WHERE lower(entry.heading) = :term
               OR EXISTS (
                   SELECT lexeme.id FROM TezaursLexeme lexeme
                   WHERE lexeme.entryId = entry.id
                     AND lower(lexeme.lemma) = :term
               )
            ORDER BY entry.heading, entry.id
            """)
    List<TezaursEntry> findExactMatches(@Param("term") String term);

    @Query("""
            SELECT entry
            FROM TezaursEntry entry
            WHERE lower(entry.heading) LIKE concat('%', :term, '%')
               OR EXISTS (
                   SELECT lexeme.id FROM TezaursLexeme lexeme
                   WHERE lexeme.entryId = entry.id
                     AND lower(lexeme.lemma) LIKE concat('%', :term, '%')
               )
            ORDER BY entry.heading, entry.id
            """)
    List<TezaursEntry> findContaining(@Param("term") String term, Pageable pageable);
}
