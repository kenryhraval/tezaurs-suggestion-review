package lv.tezaurs.tip.entry.repository;

import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.entry.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRepository extends JpaRepository<Entry, UUID> {
    Optional<Entry> findByReviewId(UUID reviewId);

    Optional<Entry> findFirstByHeadingOrderByHomonymNoDesc(String heading);
}
