package lv.tezaurs.suggestions.usage.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import lv.tezaurs.suggestions.usage.dto.Corpus;
import lv.tezaurs.suggestions.usage.error.CorpusUnavailableException;
import lv.tezaurs.suggestions.usage.dto.CorpusSearchResponse;

@Service
public class CorpusSearchService {
    private static final int PAGE_SIZE = 5;
    private static final String CASE_INSENSITIVE_PREFIX = "(?i)";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public CorpusSearchResponse search(String query, Corpus corpus, int page) {
        return search(query, corpus, page, corpus.apiUrl);
    }

    CorpusSearchResponse search(String query, Corpus corpus, int page, String corpusApiUrl) {
        String term = query.trim();
        URI requestUrl = UriComponentsBuilder.fromUriString(corpusApiUrl + "/view")
                .queryParam("corpname", corpus.corpusId)
                .queryParam("q", buildCorpusQuery(term, corpus))
                .queryParam("format", "json")
                .queryParam("viewmode", "sen")
                .queryParam("refs", corpus.references)
                .queryParam("pagesize", PAGE_SIZE)
                .queryParam("fromp", page)
                .build().encode().toUri();
        HttpRequest request = HttpRequest.newBuilder(requestUrl)
                .timeout(Duration.ofSeconds(12)).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Corpus returned HTTP " + response.statusCode());
            }
            JsonNode data = json.readTree(response.body());
            String error = data.path("error").asString("");
            if (!error.isBlank() || !data.path("Lines").isArray() || !data.path("concsize").isIntegralNumber()) {
                throw new IOException("Corpus returned an error or an incomplete response");
            }
            List<CorpusSearchResponse.Example> examples = new ArrayList<>();
            for (JsonNode line : data.path("Lines")) {
                if (!line.path("toknum").isIntegralNumber() || line.path("toknum").asLong() < 0) {
                    throw new IOException("Corpus returned an example without a valid token number");
                }
                long tokenNumber = line.path("toknum").asLong();
                String leftContext = readCorpusText(line.path("Left"));
                String matchedText = readCorpusText(line.path("Kwic"));
                String rightContext = readCorpusText(line.path("Right"));
                List<String> references = new ArrayList<>();
                for (JsonNode reference : line.path("Refs")) {
                    String value = reference.asString("");
                    if (!value.isBlank()) references.add(value);
                }
                String reference = String.join("; ", references);
                var source = UsageReferenceFormatter.format(corpus, reference, null);
                String citation = (leftContext + matchedText + rightContext).trim();
                examples.add(new CorpusSearchResponse.Example(tokenNumber, leftContext, matchedText, rightContext,
                        citation, reference, source.label(), source.url()));
            }
            String sourceUrl = UriComponentsBuilder.fromUri(requestUrl)
                    .replaceQueryParam("format").build(true).toUriString();
            return new CorpusSearchResponse(corpus, corpus.label, term, page, PAGE_SIZE,
                    data.path("concsize").asLong(), sourceUrl, examples);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CorpusUnavailableException(exception);
        } catch (IOException | IllegalArgumentException | JacksonException exception) {
            throw new CorpusUnavailableException(exception);
        }
    }

    private static String readCorpusText(JsonNode segments) {
        StringBuilder combinedText = new StringBuilder();
        for (JsonNode segment : segments) {
            boolean isAnnotation = segment.path("class").asString("").equals("attr");
            if (isAnnotation) {
                continue;
            }
            String segmentText = segment.path("str").asString("");
            combinedText.append(segmentText);
        }
        return combinedText.toString();
    }

    private static String buildCorpusQuery(String term, Corpus corpus) {
        String[] words = term.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder corpusQuery = new StringBuilder("q");

        // The corpus API expects q[attribute="value"|other="value"], with one bracket pair per word.
        for (String word : words) {
            String escapedWord = escapeQueryLiteral(word);
            List<String> attributeMatches = new ArrayList<>();

            for (String attribute : corpus.searchAttributes) {
                String searchValue = escapedWord;
                if (attribute.equals("word")) {
                    // Unlike lc and lemma_lc, the word attribute preserves letter case.
                    searchValue = CASE_INSENSITIVE_PREFIX + escapedWord;
                }
                String attributeMatch = attribute + "=\"" + searchValue + "\"";
                attributeMatches.add(attributeMatch);
            }

            String matchAnyAttribute = String.join("|", attributeMatches);
            corpusQuery.append('[').append(matchAnyAttribute).append(']');
        }
        return corpusQuery.toString();
    }

    private static String escapeQueryLiteral(String word) {
        StringBuilder escaped = new StringBuilder();
        for (char character : word.toCharArray()) {
            if ("\\.^$|?*+()[]{}\"".indexOf(character) >= 0) escaped.append('\\');
            escaped.append(character);
        }
        return escaped.toString();
    }
}
