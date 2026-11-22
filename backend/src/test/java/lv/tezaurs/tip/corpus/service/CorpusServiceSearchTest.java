package lv.tezaurs.tip.corpus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.CorpusUnavailableException;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.corpus.dto.CorpusSearchResponse;
import lv.tezaurs.tip.example.service.ExampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorpusServiceSearchTest {
    private HttpServer server;
    private CorpusService service;
    private String requestQuery;
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void startServer() throws Exception {
        responseStatus = 200;
        responseBody = "{\"concsize\":0,\"Lines\":[]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/view", exchange -> {
            requestQuery = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        service = new CorpusService(mock(ReviewRepository.class), mock(ExampleService.class), mock(Validator.class));
    }

    private CorpusSearchResponse search(String query, Corpus corpus, int page) {
        return search(query, corpus, page, false);
    }

    private CorpusSearchResponse search(String query, Corpus corpus, int page, boolean exactOnly) {
        String localUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return service.requestCorpusPage(query, corpus, page, exactOnly, localUrl);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void convertsCorpusHitsAndPreservesDocumentReferences() {
        responseBody = """
                {"concsize":42,"Lines":[{"toknum":12345,"Left":[{"str":"Viņš ","class":""},{"str":"tag","class":"attr"}],
                "Kwic":[{"str":"lemj","class":"col0"}],"Right":[{"str":" par lietu.","class":""}],
                "Refs":["Avots=tests","ID=doc-1",""]}]}
                """;
        var result = search("lemt", Corpus.LVK2022, 2);
        assertThat(result.total()).isEqualTo(42);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(25);
        assertThat(result.examples()).singleElement().satisfies(example -> {
            assertThat(example.citation()).isEqualTo("Viņš lemj par lietu.");
            assertThat(example.reference()).isEqualTo("Avots=tests; ID=doc-1");
            assertThat(example.match()).isEqualTo("lemj");
            assertThat(example.tokenNumber()).isEqualTo(12345L);
            assertThat(example.sourceLabel()).isEqualTo("tests");
            assertThat(example.sourceUrl()).isNull();
        });
        assertThat(requestQuery).contains("corpname=LVK2022", "q=q[lc=\"lemt\"|lemma_lc=\"lemt\"]", "fromp=2", "viewmode=sen", "pagesize=25");
        assertThat(result.url()).doesNotContain("format=json");
    }

    @Test
    void requestsARandomSampleWithItsSizeInsteadOfBrowsingPagesOfTheFullConcordance() {
        responseBody = """
                {"concsize":1,"Lines":[{"toknum":123,"Left":[],"Kwic":[{"str":"lemt"}],"Right":[],"Refs":[]}]}
                """;
        String localUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        var result = service.requestCorpusPage("lemt", Corpus.LVK2022, 1, true, localUrl, 50);

        assertThat(requestQuery).contains("q=q[lc=\"lemt\"]", "q=r50", "pagesize=50", "fromp=1");
        assertThat(result.pageSize()).isEqualTo(50);
        assertThat(result.examples()).singleElement().satisfies(example -> {
            assertThat(example.tokenNumber()).isEqualTo(123);
            assertThat(example.citation()).isEqualTo("lemt");
        });
    }

    @Test
    void fetchesLargeSamplesInBatchesFromTheSameReducedConcordance() {
        String localUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        var result = service.requestCorpusPage("lemt", Corpus.LVK2022, 2, false, localUrl, 1500);

        assertThat(requestQuery).contains("q=r1500", "pagesize=1000", "fromp=2");
        assertThat(result.pageSize()).isEqualTo(1000);
    }

    @Test
    void removesCorpusMarkupWithoutLosingTheHighlightedMatchOrTextSpacing() {
        responseBody = """
                {"concsize":1,"Lines":[{"toknum":12,
                "Left":[{"str":"<doc id='1'>","class":"strc"},{"str":"<p>Viņš &amp; viņa ","class":""}],
                "Kwic":[{"str":"lemj","class":"col0"}],
                "Right":[{"str":" par lietu.</p> <p>Tālāk.</p>","class":""}],"Refs":[]}]}
                """;

        assertThat(search("lemt", Corpus.LVK2022, 1).examples()).singleElement().satisfies(example -> {
            assertThat(example.left()).isEqualTo(" Viņš & viņa ");
            assertThat(example.match()).isEqualTo("lemj");
            assertThat(example.right()).isEqualTo(" par lietu. Tālāk. ");
            assertThat(example.citation()).isEqualTo("Viņš & viņa lemj par lietu. Tālāk.");
        });
    }

    @Test
    void searchesPhrasesWithoutTreatingPunctuationAsQuerySyntax() {
        var result = search("A.B jauns", Corpus.TIMEKLIS, 1);
        assertThat(requestQuery).contains("corpname=timeklis", "q=q[lc=\"a\\.b\"|lemma_lc=\"a\\.b\"][lc=\"jauns\"|lemma_lc=\"jauns\"]");
        assertThat(result.total()).isZero();
        assertThat(result.examples()).isEmpty();
    }

    @Test
    void searchesBothLemmaAndFormAutomatically() {
        search("LeMt", Corpus.LVK2022, 1);
        assertThat(requestQuery).contains("q=q[lc=\"lemt\"|lemma_lc=\"lemt\"]");
    }

    @Test
    void exactSearchUsesOnlyWrittenFormsAcrossAllCorpora() {
        for (Corpus corpus : Corpus.values()) {
            search("A.B jauns", corpus, 2, true);
            String expectedQuery = "q=q[lc=\"a\\.b\"][lc=\"jauns\"]";
            if (List.of(corpus.searchAttributes).contains("word")) {
                expectedQuery = "q=q[word=\"(?i)a\\.b\"][word=\"(?i)jauns\"]";
            }
            assertThat(requestQuery).contains(expectedQuery, "fromp=2")
                    .doesNotContain("lemma_lc", "translit_lc");
        }
    }

    @Test
    void requestsReadableDocumentDetailsWhereTheCorpusProvidesThem() {
        search("lemt", Corpus.ZINAS, 1);
        assertThat(requestQuery).contains("refs=doc.title,doc.publish_time,doc.url");

        search("lemt", Corpus.LAT_SEN_ROM, 1);
        assertThat(requestQuery).contains("refs=doc.creator,doc.workTitle,doc.firstEdition,doc.publicationDate");

        search("vārds", Corpus.BAROMETRS, 1);
        assertThat(requestQuery).contains("refs=doc.year,doc.article_title,doc.date");

        search("likums", Corpus.LIKUMI, 1);
        assertThat(requestQuery).contains("doc.legislator");

        search("vārds", Corpus.SAEIMA, 1);
        assertThat(requestQuery).contains("doc.category");

        search("vārds", Corpus.TVITEDIENS, 1);
        assertThat(requestQuery).contains("doc.year");

        search("vārds", Corpus.LATVJU_DAINAS, 1);
        assertThat(requestQuery).contains("doc.section");

        search("vārds", Corpus.UDLV_LVTB, 1);
        assertThat(requestQuery).contains("s.id");
    }

    @Test
    void formatsProviderReferencesAndPreservesTheRawMetadata() {
        responseBody = """
                {"concsize":1,"Lines":[{"toknum":123,"Left":[],"Kwic":[{"str":"vārds"}],"Right":[],
                "Refs":["doc.title=Raksts","doc.publish_time=2024","doc.url=https://example.com/article?id=2"]}]}
                """;
        var example = search("vārds", Corpus.ZINAS, 1).examples().getFirst();
        assertThat(example.sourceLabel()).isEqualTo("\"Raksts\", 2024");
        assertThat(example.sourceUrl()).isEqualTo("https://example.com/article?id=2");
        assertThat(example.reference()).isEqualTo("doc.title=Raksts; doc.publish_time=2024; doc.url=https://example.com/article?id=2");
    }

    @Test
    void usesTheAttributesAvailableInEachCorpus() {
        search("Vārds", Corpus.SENIE_UNICODE, 1);
        assertThat(requestQuery).contains("q=q[lc=\"vārds\"|translit_lc=\"vārds\"]");

        search("Vārds", Corpus.MULA, 1);
        assertThat(requestQuery).contains("q=q[lc=\"vārds\"]").doesNotContain("lemma");

        search("Vārds", Corpus.MULAR, 1);
        assertThat(requestQuery).contains("q=q[word=\"(?i)vārds\"]");

    }

    @Test
    void includesTheCollectionAndRoutesCorporaToTheirHostingServers() {
        assertThat(Corpus.values()).hasSize(42);
        assertThat(Corpus.LVK2022.apiUrl).isEqualTo("https://nosketch.korpuss.lv/bonito/run.cgi");
        assertThat(Corpus.LAT_SEN_ROM.apiUrl).isEqualTo("https://korpuss.lnb.lv/bonito/run.cgi");
        assertThat(Corpus.PANDEMIJAS_DIENASGRAMATAS.apiUrl).isEqualTo("https://nosketch.lnb.lv/bonito/run.cgi");
        for (Corpus corpus : Corpus.values()) {
            search("vārds", corpus, 1);
            assertThat(requestQuery).contains("corpname=" + corpus.corpusId);
        }
    }

    @Test
    void distinguishesUpstreamErrorsFromAnEmptyResult() {
        for (String invalid : new String[]{"not json", "{\"error\":\"Unknown corpus\"}", "{}", "{\"concsize\":1,\"Lines\":[{}]}"}) {
            responseBody = invalid;
            assertThatThrownBy(() -> search("lemt", Corpus.LVK2022, 1))
                    .isInstanceOf(CorpusUnavailableException.class);
        }
        responseStatus = 503;
        assertThatThrownBy(() -> search("lemt", Corpus.LVK2022, 1))
                .isInstanceOf(CorpusUnavailableException.class);
    }
}
