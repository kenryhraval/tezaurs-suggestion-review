package lv.tezaurs.tip.tezaurs.repository;

import java.util.List;
import lv.tezaurs.tip.tezaurs.entity.TezaursGrammarFlag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TezaursGrammarFlagRepository extends JpaRepository<TezaursGrammarFlag, Integer> {
    @EntityGraph(attributePaths = "values")
    List<TezaursGrammarFlag> findAllByDeprecatedFalseOrderByOrderNoAscNameAsc();
}
