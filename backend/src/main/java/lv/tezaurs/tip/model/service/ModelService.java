package lv.tezaurs.tip.model.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.SenseModelException;
import org.springframework.core.io.Resource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lv.tezaurs.tip.model.dto.ModelOption;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConfigurationProperties("model")
@Getter
@Setter
@Slf4j
public class ModelService {
    private String defaultModel;
    private Map<String, Model> models = new LinkedHashMap<>();
    private Map<String, Resource> prompts = new LinkedHashMap<>();
    private Duration timeout = Duration.ofSeconds(90);
    private int maxInputCharacters = 250_000;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Getter
    @Setter
    public static class Model {
        private String label;
        private String baseUrl;
        private String model;
        private String apiKey = "";
    }

    public <T> T requestStructuredResponse(String operation, String modelId, Object input, Class<T> responseType) {
        String selectedModelId = modelId == null || modelId.isBlank() ? defaultModel : modelId;
        Model selectedModel = requireConfiguredModel(selectedModelId);
        Resource prompt = prompts.get(operation);
        if (prompt == null) {
            throw new ConflictException("MI uzvedne nav konfigurēta darbībai “" + operation + "”.");
        }
        String inputJson = json.writeValueAsString(input);
        if (inputJson.length() > maxInputCharacters) {
            throw new ConflictException("MI ievaddatos ir " + inputJson.length() + " rakstzīmes; atļauts līdz "
                    + maxInputCharacters + ". Samaziniet piemēru skaitu.");
        }
        String stage = "prepare request";
        Integer httpStatus = null;
        String finishReason = "unavailable";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", selectedModel.getModel());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", prompt.getContentAsString(StandardCharsets.UTF_8)),
                    Map.of("role", "user", "content", inputJson)));
            body.put("response_format", Map.of("type", "json_object"));
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(selectedModel.getBaseUrl().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (!selectedModel.getApiKey().isBlank()) {
                request.header("Authorization", "Bearer " + selectedModel.getApiKey());
            }
            stage = "provider request";
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            httpStatus = response.statusCode();
            stage = "check HTTP status";
            if (httpStatus != 200) {
                throw new IOException("Unexpected model response");
            }
            stage = "check response size";
            if (response.body().length() > 1_000_000) {
                throw new IOException("Model response is too large");
            }
            stage = "parse provider response";
            var choice = json.readTree(response.body()).path("choices").path(0);
            finishReason = choice.path("finish_reason").asString("");
            stage = "check completion";
            if (!"stop".equals(finishReason)) {
                throw new IOException("Model response was incomplete");
            }
            stage = "parse generated JSON";
            String content = choice.path("message").path("content").asString("");
            return json.readValue(content, responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("AI request interrupted: operation={}, modelId={}, model={}",
                    operation, selectedModelId, selectedModel.getModel());
            throw new SenseModelException(exception);
        } catch (IOException | RuntimeException exception) {
            log.warn("AI request failed: operation={}, modelId={}, model={}, stage={}, httpStatus={}, finishReason={}, errorType={}",
                    operation, selectedModelId, selectedModel.getModel(), stage, httpStatus, finishReason,
                    exception.getClass().getSimpleName());
            throw new SenseModelException(exception);
        }
    }

    private Model requireConfiguredModel(String modelId) {
        String selectedModelId = modelId == null || modelId.isBlank() ? defaultModel : modelId;
        Model selectedModel = models.get(selectedModelId);
        if (selectedModel == null || selectedModel.getBaseUrl() == null || selectedModel.getBaseUrl().isBlank()
                || selectedModel.getModel() == null || selectedModel.getModel().isBlank()) {
            throw new ConflictException("Izvēlētais MI modelis nav konfigurēts.");
        }

        return selectedModel;
    }

    public List<ModelOption> getAvailableModelOptions() {
        return models.entrySet().stream().map(entry -> new ModelOption(
                entry.getKey(), entry.getValue().getLabel(), entry.getKey().equals(defaultModel))).toList();
    }
}
