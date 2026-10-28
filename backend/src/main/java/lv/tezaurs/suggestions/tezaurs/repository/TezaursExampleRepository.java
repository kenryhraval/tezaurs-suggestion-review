package lv.tezaurs.suggestions.tezaurs.repository;

import java.util.List;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursExample;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TezaursExampleRepository extends JpaRepository<TezaursExample, Integer> {
    List<TezaursExample> findAllByEntryIdOrderBySenseIdAscOrderNoAscIdAsc(Integer entryId);
}
