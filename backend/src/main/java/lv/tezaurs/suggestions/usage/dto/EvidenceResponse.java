package lv.tezaurs.suggestions.usage.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.suggestions.usage.entity.EvidenceSource;
import lv.tezaurs.suggestions.usage.entity.UsageEvidence;
import lv.tezaurs.suggestions.usage.service.UsageReferenceFormatter;

public record EvidenceResponse(UUID id, EvidenceSource source, Corpus corpus, String corpusLabel, Long tokenNumber,
        String query, String citation, String url, String reference, String note,
        String createdBy, Instant createdAt, String sourceLabel, String sourceUrl) {
    public static EvidenceResponse from(UsageEvidence evidence) {
        var source = UsageReferenceFormatter.format(evidence.getCorpus(), evidence.getReference(), evidence.getUrl());
        return new EvidenceResponse(evidence.getId(), evidence.getSource(), evidence.getCorpus(),
                evidence.getCorpus() == null ? null : evidence.getCorpus().label, evidence.getTokenNumber(), evidence.getQuery(), evidence.getCitation(), evidence.getUrl(),
                evidence.getReference(), evidence.getNote(), evidence.getCreatedBy(), evidence.getCreatedAt(),
                source.label(), source.url());
    }
}
