package lv.tezaurs.suggestions.entry.repository;

import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewEntryRepository extends JpaRepository<ReviewEntry, UUID> {
    Optional<ReviewEntry> findByCaseId(UUID caseId);

    Optional<ReviewEntry> findFirstByHeadingAndIdNotOrderByHomonymNoDesc(String heading, UUID id);
}
