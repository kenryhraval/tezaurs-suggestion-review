package lv.tezaurs.suggestions.usage.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.usage.entity.UsageEvidence;
import lv.tezaurs.suggestions.usage.dto.Corpus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageEvidenceRepository extends JpaRepository<UsageEvidence, UUID> {
    List<UsageEvidence> findByCaseIdOrderByCreatedAtAscIdAsc(UUID caseId);
    Optional<UsageEvidence> findByIdAndCaseId(UUID id, UUID caseId);
    Optional<UsageEvidence> findByCaseIdAndCorpusAndTokenNumber(UUID caseId, Corpus corpus, Long tokenNumber);
}
