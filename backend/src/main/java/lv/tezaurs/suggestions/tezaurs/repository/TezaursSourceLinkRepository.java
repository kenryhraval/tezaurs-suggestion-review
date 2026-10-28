package lv.tezaurs.suggestions.tezaurs.repository;

import java.util.List;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursSourceLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TezaursSourceLinkRepository extends JpaRepository<TezaursSourceLink, Integer> {
    @EntityGraph(attributePaths = "source")
    List<TezaursSourceLink> findAllByEntryIdOrderByOrderNoAscIdAsc(Integer entryId);
}
