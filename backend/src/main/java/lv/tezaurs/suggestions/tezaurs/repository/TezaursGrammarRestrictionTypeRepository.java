package lv.tezaurs.suggestions.tezaurs.repository;

import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarRestrictionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TezaursGrammarRestrictionTypeRepository
        extends JpaRepository<TezaursGrammarRestrictionType, Integer> {
    boolean existsByCaption(String caption);
}
