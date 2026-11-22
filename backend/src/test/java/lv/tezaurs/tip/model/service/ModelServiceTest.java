package lv.tezaurs.tip.model.service;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lv.tezaurs.tip.sense.dto.ClusteredSenses;
import lv.tezaurs.tip.sense.dto.SenseDefinitions;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.SenseModelException;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ModelServiceTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final ModelService settings = new ModelService();
    private final ModelService.Model localModel = new ModelService.Model();
    private HttpServer server;
    private String lastRequestPath;
    private JsonNode lastRequest;
    private String lastAuthorization;
    private int status = 200;
    private String content;
    private String finishReason = "stop";

    @BeforeEach
    void startProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var context = server.createContext("/v1/chat/completions", exchange -> {
            lastRequestPath = exchange.getRequestURI().getPath();
            lastRequest = json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            String response = json.writeValueAsString(Map.of("choices", java.util.List.of(Map.of(
                    "finish_reason", finishReason, "message", Map.of("content", content)))));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/alternate/chat/completions", context.getHandler());
        server.start();
        localModel.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        localModel.setModel("local-model");
        settings.setDefaultModel("local");
        settings.getModels().put("local", localModel);
        settings.getPrompts().put("clustering", new ByteArrayResource("Cluster prompt\n\nCluster format".getBytes(StandardCharsets.UTF_8)));
        settings.getPrompts().put("definitions", new ByteArrayResource("Definition prompt\n\nDefinition format".getBytes(StandardCharsets.UTF_8)));
    }

    @AfterEach
    void stopProvider() {
        server.stop(0);
    }

    @Test
    void sendsStandardChatRequestsWithConfigurablePromptsAndParsesClusterIds() {
        content = "{\"groups\":[{\"description\":\"Nozīme\",\"senseId\":null,\"exampleIds\":[1,2]}]}";
        localModel.setApiKey("test-key");
        var result = cluster(null, Map.of("term", "vārds"));
        assertThat(result.groups().getFirst().exampleIds()).containsExactly(1, 2);
        assertThat(lastAuthorization).isEqualTo("Bearer test-key");
        assertThat(lastRequest.path("model").asString()).isEqualTo("local-model");
        assertThat(lastRequest.path("messages").path(0).path("content").asString()).isEqualTo("Cluster prompt\n\nCluster format");
        assertThat(lastRequest.path("response_format").path("type").asString()).isEqualTo("json_object");
        assertThat(lastRequest.toString()).doesNotContain("test-key");
    }

    @Test
    void sendsReviewerGuidanceAlongsideDataWithoutReplacingThePrompt() {
        content = "{\"groups\":[]}";

        settings.requestStructuredResponse("clustering", null, "Atšķir pārnesto nozīmi.", Map.of("term", "vārds"), ClusteredSenses.class);

        assertThat(settings.getPrompt("clustering")).isEqualTo("Cluster prompt\n\nCluster format");
        assertThat(lastRequest.path("messages").path(0).path("content").asString()).isEqualTo("Cluster prompt\n\nCluster format");
        assertThat(lastRequest.path("messages").path(1).path("content").asString())
                .contains("Atšķir pārnesto nozīmi.", "vārds");
    }

    @Test
    void localProvidersCanUseNoKeyWhileStillRequestingJson() {
        content = "{\"definitions\":[{\"senseId\":1,\"definition\":\"Skaidrojums\"}]}";
        var result = settings.requestStructuredResponse("definitions", null, null, Map.of("groups", java.util.List.of()), SenseDefinitions.class);
        assertThat(result.definitions().getFirst().senseId()).isEqualTo(1);
        assertThat(lastAuthorization).isNull();
        assertThat(lastRequest.path("response_format").path("type").asString()).isEqualTo("json_object");
        assertThat(lastRequest.path("messages").path(0).path("content").asString()).isEqualTo("Definition prompt\n\nDefinition format");
    }

    @Test
    void selectedModelUsesItsOwnEndpointNameAndKey() {
        var alternate = new ModelService.Model();
        alternate.setBaseUrl(localModel.getBaseUrl().replace("/v1/", "/alternate"));
        alternate.setModel("alternate-model");
        alternate.setApiKey("alternate-key");
        settings.getModels().put("alternate", alternate);
        content = "{\"groups\":[]}";

        cluster("alternate", Map.of());

        assertThat(lastRequestPath).isEqualTo("/alternate/chat/completions");
        assertThat(lastRequest.path("model").asString()).isEqualTo("alternate-model");
        assertThat(lastAuthorization).isEqualTo("Bearer alternate-key");
    }

    @Test
    void checksTheConfiguredInputSizeBeforeCallingTheProvider() {
        settings.setMaxInputCharacters(100);
        assertThatThrownBy(() -> cluster(null, Map.of("term", "a".repeat(101))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("100");
        assertThat(lastRequest).isNull();

        settings.setMaxInputCharacters(200);
        content = "{\"groups\":[]}";
        assertThat(cluster(null, Map.of("term", "a".repeat(101))).groups()).isEmpty();
        assertThat(lastRequest).isNotNull();
    }

    @Test
    void unknownModelIsRejectedBeforeMakingAnExternalRequest() {
        assertThatThrownBy(() -> cluster("unknown", Map.of()))
                .isInstanceOf(ConflictException.class);
        assertThat(lastRequest).isNull();
    }

    @Test
    void unconfiguredModelDoesNotMakeAnExternalRequest() {
        localModel.setModel("");
        assertThatThrownBy(() -> cluster(null, Map.of())).isInstanceOf(ConflictException.class);
        assertThat(lastRequest).isNull();
    }

    @Test
    void logsFailureDetailsWithoutRequestResponseOrCredentials() {
        var logger = (Logger) LoggerFactory.getLogger(ModelService.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            localModel.setApiKey("private-api-key");
            content = "private-model-output";
            var input = Map.of("examples", "private-example-text");
            status = 429;
            assertThatThrownBy(() -> cluster(null, input)).isInstanceOf(SenseModelException.class);
            status = 200;
            finishReason = "length";
            assertThatThrownBy(() -> cluster(null, input)).isInstanceOf(SenseModelException.class);
            finishReason = "stop";
            assertThatThrownBy(() -> cluster(null, input)).isInstanceOf(SenseModelException.class);

            var messages = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).hasSize(3);
            assertThat(messages.get(0)).contains("operation=clustering", "modelId=local", "model=local-model",
                    "stage=check HTTP status", "httpStatus=429");
            assertThat(messages.get(1)).contains("stage=check completion", "finishReason=length");
            assertThat(messages.get(2)).contains("stage=parse generated JSON", "httpStatus=200");
            assertThat(String.join("\n", messages)).doesNotContain("private-api-key", "private-example-text", "private-model-output");
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void rejectsFailedTruncatedAndMalformedProviderResponsesWithoutLeakingTheirContents() {
        content = "{\"groups\":[]}";
        status = 429;
        assertThatThrownBy(() -> cluster(null, Map.of()))
                .isInstanceOf(SenseModelException.class).hasMessageNotContaining("429");
        status = 200;
        finishReason = "length";
        assertThatThrownBy(() -> cluster(null, Map.of())).isInstanceOf(SenseModelException.class);
        finishReason = "stop";
        content = "not JSON";
        assertThatThrownBy(() -> cluster(null, Map.of())).isInstanceOf(SenseModelException.class);
    }
    private ClusteredSenses cluster(String modelId, Object input) {
        return settings.requestStructuredResponse("clustering", modelId, null, input, ClusteredSenses.class);
    }
}
