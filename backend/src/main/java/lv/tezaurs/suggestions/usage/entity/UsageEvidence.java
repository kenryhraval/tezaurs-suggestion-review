package lv.tezaurs.suggestions.usage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lv.tezaurs.suggestions.usage.dto.Corpus;
import lv.tezaurs.suggestions.usage.dto.SaveEvidenceRequest;

@Entity
@Table(name = "usage_evidence", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsageEvidence {
    @Id
    private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EvidenceSource source;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Corpus corpus;
    @Column(name = "token_number")
    private Long tokenNumber;
    @Column(name = "search_query", nullable = false)
    private String query;
    @Column(nullable = false, columnDefinition = "text")
    private String citation;
    @Column(nullable = false, columnDefinition = "text")
    private String url;
    @Column(columnDefinition = "text")
    private String reference;
    @Column(columnDefinition = "text")
    private String note;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UsageEvidence(UUID caseId, SaveEvidenceRequest request, String username) {
        id = UUID.randomUUID();
        this.caseId = caseId;
        source = request.source();
        corpus = request.corpus();
        tokenNumber = request.tokenNumber();
        query = request.query().trim();
        citation = request.citation().trim();
        url = request.url().trim();
        reference = request.reference();
        note = request.note();
        createdBy = username;
        createdAt = Instant.now();
    }
}
