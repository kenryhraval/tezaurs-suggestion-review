package lv.tezaurs.suggestions.tezaurs.repository;

import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TezaursEntryRepository extends JpaRepository<TezaursEntry, Integer> {
    @EntityGraph(attributePaths = "lexemes")
    List<TezaursEntry> findByHeadingIgnoreCase(String heading);

    Optional<TezaursEntry> findFirstByHeadingOrderByHomonymNoDesc(String heading);
}
