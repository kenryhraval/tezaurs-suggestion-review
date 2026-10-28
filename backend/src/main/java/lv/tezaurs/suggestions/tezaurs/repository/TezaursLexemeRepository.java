package lv.tezaurs.suggestions.tezaurs.repository;

import java.util.List;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursLexeme;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TezaursLexemeRepository extends JpaRepository<TezaursLexeme, Integer> {
    @EntityGraph(attributePaths = "entry")
    List<TezaursLexeme> findByLemmaIgnoreCase(String lemma);

    List<TezaursLexeme> findAllByEntryIdOrderByOrderNoAscIdAsc(Integer entryId);

    @Query("""
            SELECT lexeme
            FROM TezaursLexeme lexeme
            JOIN FETCH lexeme.entry entry
            WHERE lower(entry.heading) LIKE concat('%', :term, '%')
               OR lower(lexeme.lemma) LIKE concat('%', :term, '%')
            ORDER BY CASE
                         WHEN lower(entry.heading) = :term OR lower(lexeme.lemma) = :term THEN 0
                         ELSE 1
                     END,
                     entry.heading,
                     lexeme.orderNo
            """)
    List<TezaursLexeme> findContaining(@Param("term") String term, Pageable pageable);
}
