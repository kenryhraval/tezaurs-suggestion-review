package lv.tezaurs.suggestions.usage.dto;

import java.util.List;

public record CorpusSearchResponse(
        Corpus corpus, String label, String query,
        int page, int pageSize, long total, String url, List<Example> examples) {
    public record Example(long tokenNumber, String left, String match, String right, String citation, String reference,
            String sourceLabel, String sourceUrl) {
    }
}
