package lv.tezaurs.tip.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lv.tezaurs.tip.common.error.SenseModelException;
import lv.tezaurs.tip.sense.dto.ClusteredSenses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ModelHttpTest {
    private final ModelService service = new ModelService();
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private volatile JsonNode receivedRequest;
    private volatile String receivedAuthorization;
    private volatile int responseStatus = 200;
    private volatile String responseBody;

    @BeforeEach
    void startModelServer() throws Exception {
        responseBody = createCompletionResponse("stop", "{\"groups\":[{\"description\":\"Skaidrojums\",\"exampleIds\":[1]}]}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            receivedRequest = json.readTree(exchange.getRequestBody().readAllBytes());
            receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var model = new ModelService.Model();
        model.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        model.setModel("local-model");
        service.getModels().put("local", model);
        service.setDefaultModel("local");
        service.getPrompts().put("clustering", new ByteArrayResource("Group examples".getBytes(StandardCharsets.UTF_8)));
    }

    @AfterEach
    void stopModelServer() {
        service.getClient().close();
        server.stop(0);
    }

    @Test
    void sendsJsonChatRequestsWithSeparateInstructionsAndParsesTheAnswer() {
        var result = service.requestStructuredResponse("clustering", null, "Use concise wording",
                Map.of("term", "vārds"), ClusteredSenses.class);

        assertThat(receivedRequest.path("model").asString()).isEqualTo("local-model");
        assertThat(receivedRequest.at("/response_format/type").asString()).isEqualTo("json_object");
        assertThat(receivedRequest.at("/messages/0/role").asString()).isEqualTo("system");
        assertThat(receivedRequest.at("/messages/0/content").asString()).isEqualTo("Group examples");
        assertThat(receivedRequest.at("/messages/1/role").asString()).isEqualTo("user");
        assertThat(receivedRequest.at("/messages/1/content").asString())
                .contains("Use concise wording", "\"term\":\"vārds\"");
        assertThat(receivedAuthorization).isNull();
        assertThat(result.groups()).singleElement().satisfies(group -> {
            assertThat(group.description()).isEqualTo("Skaidrojums");
            assertThat(group.exampleIds()).containsExactly(1);
        });
    }

    @Test
    void usesTheSelectedModelsNameAndCredentials() {
        var selected = new ModelService.Model();
        selected.setBaseUrl(service.getModels().get("local").getBaseUrl());
        selected.setModel("another-model");
        selected.setApiKey("test-token");
        service.getModels().put("selected", selected);

        service.requestStructuredResponse("clustering", "selected", null, Map.of(), ClusteredSenses.class);

        assertThat(receivedRequest.path("model").asString()).isEqualTo("another-model");
        assertThat(receivedAuthorization).isEqualTo("Bearer test-token");
    }

    @Test
    void convertsHttpFailuresToAnApplicationErrorWithoutExposingProviderContent() {
        responseStatus = 429;
        responseBody = "private provider details";

        assertThatThrownBy(() -> service.requestStructuredResponse("clustering", null, null, Map.of(), ClusteredSenses.class))
                .isInstanceOf(SenseModelException.class).hasMessageNotContaining(responseBody);
    }

    @ParameterizedTest
    @ValueSource(strings = {"length", "content_filter", ""})
    void rejectsIncompleteCompletionsEvenWhenTheirJsonIsValid(String finishReason) {
        responseBody = createCompletionResponse(finishReason, "{\"groups\":[]}");

        assertThatThrownBy(() -> service.requestStructuredResponse("clustering", null, null, Map.of(), ClusteredSenses.class))
                .isInstanceOf(SenseModelException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}"})
    void rejectsMalformedProviderResponses(String body) {
        responseBody = body;

        assertThatThrownBy(() -> service.requestStructuredResponse("clustering", null, null, Map.of(), ClusteredSenses.class))
                .isInstanceOf(SenseModelException.class);
    }

    @Test
    void rejectsInvalidGeneratedJson() {
        responseBody = createCompletionResponse("stop", "not-json");

        assertThatThrownBy(() -> service.requestStructuredResponse("clustering", null, null, Map.of(), ClusteredSenses.class))
                .isInstanceOf(SenseModelException.class);
    }

    private String createCompletionResponse(String finishReason, String content) {
        return json.writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", finishReason, "message", Map.of("content", content)))));
    }
}
