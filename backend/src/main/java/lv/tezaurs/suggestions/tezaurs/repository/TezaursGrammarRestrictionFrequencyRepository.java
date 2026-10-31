package lv.tezaurs.suggestions.tezaurs.repository;

import lv.tezaurs.suggestions.tezaurs.entity.TezaursGrammarRestrictionFrequency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TezaursGrammarRestrictionFrequencyRepository
        extends JpaRepository<TezaursGrammarRestrictionFrequency, Integer> {
    boolean existsByCaption(String caption);
}
