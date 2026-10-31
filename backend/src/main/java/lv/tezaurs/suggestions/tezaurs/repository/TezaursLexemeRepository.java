package lv.tezaurs.suggestions.tezaurs.repository;

import java.util.List;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursLexeme;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TezaursLexemeRepository extends JpaRepository<TezaursLexeme, Integer> {
    List<TezaursLexeme> findAllByEntryIdOrderByOrderNoAscIdAsc(Integer entryId);

}
