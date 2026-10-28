package lv.tezaurs.suggestions.tezaurs.repository;

import java.util.List;
import lv.tezaurs.suggestions.tezaurs.entity.TezaursSense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TezaursSenseRepository extends JpaRepository<TezaursSense, Integer> {
    boolean existsByIdAndEntryId(Integer id, Integer entryId);

    @Query("""
            SELECT sense
            FROM TezaursSense sense
            WHERE sense.entryId = :entryId
            ORDER BY sense.parentSenseId NULLS FIRST,
                     sense.orderNo NULLS LAST,
                     sense.id
            """)
    List<TezaursSense> findOrderedByEntryId(@Param("entryId") Integer entryId);
}
