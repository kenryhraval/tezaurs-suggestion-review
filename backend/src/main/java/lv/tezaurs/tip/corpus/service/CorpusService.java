package lv.tezaurs.tip.corpus.service;

import jakarta.validation.Validator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.CorpusUnavailableException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.common.util.CorpusTextFormatter;
import lv.tezaurs.tip.common.util.ExampleSourceFormatter;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.corpus.dto.CorpusSearchResponse;
import lv.tezaurs.tip.example.dto.ExampleResponse;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.dto.SelectCorpusExamplesRequest;
import lv.tezaurs.tip.example.entity.ExampleSource;
import lv.tezaurs.tip.example.service.ExampleService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class CorpusService {
    private static final int PAGE_SIZE = 25;
    private static final int SAMPLE_PAGE_SIZE = 1000;
    private static final String CASE_INSENSITIVE_PREFIX = "(?i)";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    private final ReviewRepository reviewRepository;
    private final ExampleService exampleService;
    private final Validator validator;

    public List<ExampleResponse> selectAndSaveCorpusExamples(UUID reviewId, SelectCorpusExamplesRequest request, String authorization) {
        requireEditableReview(reviewId);

        Set<Long> selectedTokenNumbers = findSelectedTokenNumbers(reviewId, request.corpus());

        List<SaveExampleRequest> examplesToSave;
        if (request.count() == null) {
            examplesToSave = fetchAllUnselectedExamples(request, selectedTokenNumbers);
        } else {
            examplesToSave = fetchRandomUnselectedExamples(request, selectedTokenNumbers);
        }

        validateExamplesBeforeSaving(examplesToSave);
        exampleService.saveExamples(reviewId, examplesToSave, authorization);

        return exampleService.getExamplesForReview(reviewId);
    }

    private void requireEditableReview(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Perēklis “" + reviewId + "” netika atrasts."));

        if (review.getStatus() != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Izmaiņas iespējamas tikai stāvoklī “Apstrādē”.");
        }
    }

    private Set<Long> findSelectedTokenNumbers(UUID reviewId, Corpus corpus) {
        return exampleService.findSelectedTokenNumbers(reviewId, corpus);
    }

    private List<SaveExampleRequest> fetchAllUnselectedExamples(SelectCorpusExamplesRequest request,
            Set<Long> selectedTokenNumbers) {
        CorpusSearchResponse firstPage = fetchCorpusPage(request, 1);
        List<SaveExampleRequest> examplesToSave = new ArrayList<>();
        long totalPages = (firstPage.total() + firstPage.pageSize() - 1) / firstPage.pageSize();

        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
            CorpusSearchResponse corpusPage = pageNumber == 1 ? firstPage : fetchCorpusPage(request, pageNumber);
            if (corpusPage.examples().isEmpty()) {
                throw corpusResultsChangedDuringSelection();
            }
            for (var corpusExample : corpusPage.examples()) {
                if (selectedTokenNumbers.add(corpusExample.tokenNumber())) {
                    examplesToSave.add(createExampleRequest(corpusPage, corpusExample));
                }
            }
        }

        return examplesToSave;
    }

    private List<SaveExampleRequest> fetchRandomUnselectedExamples(SelectCorpusExamplesRequest request,
            Set<Long> selectedTokenNumbers) {
        int sampleSizeIncludingSavedExamples = Math.addExact(request.count(), selectedTokenNumbers.size());
        CorpusSearchResponse firstPage = sampleExamples(request, sampleSizeIncludingSavedExamples, 1);
        List<SaveExampleRequest> availableExamples = new ArrayList<>();
        long totalPages = (firstPage.total() + firstPage.pageSize() - 1) / firstPage.pageSize();

        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
            CorpusSearchResponse samplePage = pageNumber == 1 ? firstPage
                    : sampleExamples(request, sampleSizeIncludingSavedExamples, pageNumber);
            if (samplePage.examples().isEmpty()) {
                throw corpusResultsChangedDuringSelection();
            }
            for (var example : samplePage.examples()) {
                if (selectedTokenNumbers.add(example.tokenNumber())) {
                    availableExamples.add(createExampleRequest(samplePage, example));
                }
            }
        }

        Collections.shuffle(availableExamples);

        return availableExamples.stream().limit(request.count()).toList();
    }

    CorpusSearchResponse sampleExamples(SelectCorpusExamplesRequest request, int sampleSize, int pageNumber) {
        return requestCorpusPage(request.query(), request.corpus(), pageNumber, request.exactOnly(),
                request.corpus().apiUrl, sampleSize);
    }

    CorpusSearchResponse fetchCorpusPage(SelectCorpusExamplesRequest request, int pageNumber) {
        return requestCorpusPage(request.query(), request.corpus(), pageNumber, request.exactOnly(),
                request.corpus().apiUrl, null, SAMPLE_PAGE_SIZE);
    }

    private SaveExampleRequest createExampleRequest(CorpusSearchResponse corpusPage, CorpusSearchResponse.Example corpusExample) {
        return new SaveExampleRequest(ExampleSource.CORPUS, corpusPage.corpus(), corpusExample.tokenNumber(),
                corpusPage.query(), corpusExample.citation(), corpusPage.url(), corpusExample.reference(), null,
                corpusExample.left().stripLeading().length(),
                corpusExample.left().stripLeading().length() + corpusExample.match().length());
    }

    private void validateExamplesBeforeSaving(List<SaveExampleRequest> examplesToSave) {
        for (SaveExampleRequest example : examplesToSave) {
            if (!validator.validate(example).isEmpty()) {
                throw new ConflictException("Korpuss atgrieza nepilnīgus vai pārāk garus piemērus. Atlase nav saglabāta.");
            }
        }
    }

    private ConflictException corpusResultsChangedDuringSelection() {
        return new ConflictException("Korpusa rezultāti mainījušies. Atkārtojiet meklēšanu; atlase nav saglabāta.");
    }

    public CorpusSearchResponse searchExamples(String query, Corpus corpus, int page, boolean exactOnly) {
        return requestCorpusPage(query, corpus, page, exactOnly, corpus.apiUrl);
    }

    CorpusSearchResponse requestCorpusPage(String query, Corpus corpus, int page, boolean exactOnly, String corpusApiUrl) {
        return requestCorpusPage(query, corpus, page, exactOnly, corpusApiUrl, null);
    }

    CorpusSearchResponse requestCorpusPage(String query, Corpus corpus, int page, boolean exactOnly,
            String corpusApiUrl, Integer sampleSize) {
        int pageSize = sampleSize == null ? PAGE_SIZE : Math.min(sampleSize, SAMPLE_PAGE_SIZE);
        return requestCorpusPage(query, corpus, page, exactOnly, corpusApiUrl, sampleSize, pageSize);
    }

    private CorpusSearchResponse requestCorpusPage(String query, Corpus corpus, int page, boolean exactOnly,
            String corpusApiUrl, Integer sampleSize, int pageSize) {
        String term = query.trim();
        UriComponentsBuilder requestUrlBuilder = UriComponentsBuilder.fromUriString(corpusApiUrl + "/view")
                .queryParam("corpname", corpus.corpusId)
                .queryParam("q", buildCorpusQuery(term, corpus, exactOnly))
                .queryParam("format", "json")
                .queryParam("viewmode", "sen")
                .queryParam("refs", corpus.references)
                .queryParam("pagesize", pageSize)
                .queryParam("fromp", page);
        if (sampleSize != null) {
            requestUrlBuilder.queryParam("q", "r" + sampleSize);
        }

        URI requestUrl = requestUrlBuilder.build().encode().toUri();
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
                var source = ExampleSourceFormatter.format(corpus, reference, null);
                String citation = (leftContext + matchedText + rightContext).trim();
                examples.add(new CorpusSearchResponse.Example(tokenNumber, leftContext, matchedText, rightContext,
                        citation, reference, source.label(), source.url()));
            }
            String sourceUrl = UriComponentsBuilder.fromUri(requestUrl)
                    .replaceQueryParam("format").build(true).toUriString();
            return new CorpusSearchResponse(corpus, corpus.label, term, page, pageSize,
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
            boolean isStructure = segment.path("class").asString("").equals("strc");
            if (isAnnotation || isStructure) {
                continue;
            }
            String segmentText = segment.path("str").asString("");
            combinedText.append(segmentText);
        }

        return CorpusTextFormatter.format(combinedText.toString());
    }

    private static String buildCorpusQuery(String term, Corpus corpus, boolean exactOnly) {
        String[] words = term.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder corpusQuery = new StringBuilder("q");

        // The corpus API expects q[attribute="value"|other="value"], with one bracket pair per word.
        for (String word : words) {
            String escapedWord = escapeQueryLiteral(word);
            List<String> attributeMatches = new ArrayList<>();

            for (String attribute : corpus.searchAttributes) {
                boolean isWrittenForm = attribute.equals("lc") || attribute.equals("word");
                if (exactOnly && !isWrittenForm) {
                    continue;
                }
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
