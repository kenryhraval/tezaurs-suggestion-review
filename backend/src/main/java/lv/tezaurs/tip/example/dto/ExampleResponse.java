package lv.tezaurs.tip.example.dto;

import java.time.Instant;
import java.util.UUID;
import lv.tezaurs.tip.common.util.CorpusTextFormatter;
import lv.tezaurs.tip.common.util.ExampleSourceFormatter;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;

public record ExampleResponse(UUID id, UUID senseId, boolean approved, ExampleSource source, Corpus corpus, String corpusLabel, Long tokenNumber,
        String query, String citation, String url, String reference, String note,
        String createdBy, Instant createdAt, String sourceLabel, String sourceUrl, Integer matchStart, Integer matchEnd) {
    public static ExampleResponse from(Example example) {
        var source = ExampleSourceFormatter.format(example.getCorpus(), example.getReference(), example.getUrl());
        String citation = example.getCitation();
        if (example.getSource() == ExampleSource.CORPUS) {
            citation = CorpusTextFormatter.format(citation).trim();
        }
        return new ExampleResponse(example.getId(), example.getSense() == null ? null : example.getSense().getId(),
                example.isApproved(), example.getSource(), example.getCorpus(),
                example.getCorpus() == null ? null : example.getCorpus().label, example.getTokenNumber(), example.getQuery(), citation, example.getUrl(),
                example.getReference(), example.getNote(), example.getCreatedBy(), example.getCreatedAt(),
                source.label(), source.url(), example.getMatchStart(), example.getMatchEnd());
    }
}
