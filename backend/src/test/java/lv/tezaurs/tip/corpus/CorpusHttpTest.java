package lv.tezaurs.tip.corpus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lv.tezaurs.tip.common.error.CorpusUnavailableException;
import lv.tezaurs.tip.corpus.dto.Corpus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CorpusHttpTest {
    private final CorpusService service = new CorpusService(null, null, null);
    private HttpServer server;
    private String baseUrl;
    private volatile URI requestedUri;
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"concsize\":0,\"Lines\":[]}";

    @BeforeEach
    void startCorpusServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/view", exchange -> {
            requestedUri = exchange.getRequestURI();
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopCorpusServer() {
        server.stop(0);
    }

    @Test
    void searchesPhrasesLiterallyAndRequestsTheChosenPage() {
        service.requestCorpusPage("  A.B vārds  ", Corpus.LVK2022, 2, false, baseUrl);

        assertThat(getRequestedQueryValues("q")).containsExactly("q[lc=\"a\\.b\"|lemma_lc=\"a\\.b\"][lc=\"vārds\"|lemma_lc=\"vārds\"]");
        assertThat(getRequestedQueryValues("corpname")).containsExactly("LVK2022");
        assertThat(getRequestedQueryValues("refs")).containsExactly(Corpus.LVK2022.references);
        assertThat(getRequestedQueryValues("fromp")).containsExactly("2");
        assertThat(getRequestedQueryValues("pagesize")).containsExactly("25");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "LVK2022 | q[lc=\"vārds\"]", "MULA | q[lc=\"vārds\"]", "MULAR | q[word=\"(?i)vārds\"]"
    })
    void exactSearchUsesTheCorporaWrittenFormAttribute(Corpus corpus, String expectedQuery) {
        service.requestCorpusPage("Vārds", corpus, 1, true, baseUrl);

        assertThat(getRequestedQueryValues("q")).containsExactly(expectedQuery);
    }

    @Test
    void requestsServerSideSamplingAndCapsItsPageSize() {
        var result = service.requestCorpusPage("vārds", Corpus.LVK2022, 2, false, baseUrl, 1200);

        assertThat(getRequestedQueryValues("q")).containsExactly("q[lc=\"vārds\"|lemma_lc=\"vārds\"]", "r1200");
        assertThat(getRequestedQueryValues("pagesize")).containsExactly("1000");
        assertThat(getRequestedQueryValues("fromp")).containsExactly("2");
        assertThat(result.pageSize()).isEqualTo(1000);
    }

    @Test
    void parsesExamplesWithoutMarkupAndPreservesTheMatchAndSource() {
        responseBody = """
                {"concsize":123,"Lines":[{"toknum":42,
                  "Left":[{"class":"strc","str":"<p>"},{"str":"Viņi "},{"class":"attr","str":"annotation"}],
                  "Kwic":[{"str":"lemj"}],"Right":[{"str":" &amp; spriež."}],
                  "Refs":["doc.title=Raksts","doc.url=https://example.com/article"]}]}
                """;

        var result = service.requestCorpusPage("lemt", Corpus.ZINAS, 1, false, baseUrl);

        assertThat(result.total()).isEqualTo(123);
        assertThat(result.examples()).singleElement().satisfies(example -> {
            assertThat(example.tokenNumber()).isEqualTo(42);
            assertThat(example.left()).isEqualTo("Viņi ");
            assertThat(example.match()).isEqualTo("lemj");
            assertThat(example.citation()).isEqualTo("Viņi lemj & spriež.");
            assertThat(example.sourceLabel()).isEqualTo("\"Raksts\"");
            assertThat(example.sourceUrl()).isEqualTo("https://example.com/article");
        });
        assertThat(result.url()).doesNotContain("format=json");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "503 | {}", "200 | not-json", "200 | {\"error\":\"unavailable\"}",
            "200 | {\"concsize\":1,\"Lines\":[{}]}"
    })
    void rejectsFailuresAndIncompleteResultsInsteadOfReportingNoMatches(int status, String body) {
        responseStatus = status;
        responseBody = body;

        assertThatThrownBy(() -> service.requestCorpusPage("vārds", Corpus.LVK2022, 1, false, baseUrl))
                .isInstanceOf(CorpusUnavailableException.class);
    }

    private List<String> getRequestedQueryValues(String name) {
        return Arrays.stream(requestedUri.getRawQuery().split("&"))
                .filter(parameter -> parameter.startsWith(name + "="))
                .map(parameter -> URLDecoder.decode(parameter.substring(name.length() + 1), StandardCharsets.UTF_8))
                .toList();
    }
}
