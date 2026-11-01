package lv.tezaurs.suggestions.usage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lv.tezaurs.suggestions.usage.dto.Corpus;
import lv.tezaurs.suggestions.usage.error.CorpusUnavailableException;
import lv.tezaurs.suggestions.usage.dto.CorpusSearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorpusSearchServiceTest {
    private HttpServer server;
    private CorpusSearchService service;
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
        service = new CorpusSearchService();
    }

    private CorpusSearchResponse search(String query, Corpus corpus, int page) {
        String localUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return service.search(query, corpus, page, localUrl);
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
        assertThat(result.pageSize()).isEqualTo(5);
        assertThat(result.examples()).singleElement().satisfies(example -> {
            assertThat(example.citation()).isEqualTo("Viņš lemj par lietu.");
            assertThat(example.reference()).isEqualTo("Avots=tests; ID=doc-1");
            assertThat(example.match()).isEqualTo("lemj");
            assertThat(example.tokenNumber()).isEqualTo(12345L);
            assertThat(example.sourceLabel()).isEqualTo("tests");
            assertThat(example.sourceUrl()).isNull();
        });
        assertThat(requestQuery).contains("corpname=LVK2022", "q=q[lc=\"lemt\"|lemma_lc=\"lemt\"]", "fromp=2", "viewmode=sen");
        assertThat(result.url()).doesNotContain("format=json");
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
    void requestsReadableDocumentDetailsWhereTheCorpusProvidesThem() {
        search("lemt", Corpus.ZINAS, 1);
        assertThat(requestQuery).contains("refs=doc.title,doc.publish_time,doc.url");

        search("lemt", Corpus.LAT_SEN_ROM, 1);
        assertThat(requestQuery).contains("refs=doc.creator,doc.workTitle,doc.firstEdition,doc.publicationDate");
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
