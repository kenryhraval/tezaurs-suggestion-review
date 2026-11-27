package lv.tezaurs.tip.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lv.tezaurs.tip.sense.dto.ClusteredSenses;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.model.dto.ModelOption;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.json.JsonMapper;

class ModelServiceTest {
    private final ModelService service = new ModelService();

    @Test
    void modelOptionsDoNotExposeProviderCredentials() {
        var model = new ModelService.Model();
        model.setLabel("Local model");
        model.setModel("provider-name");
        model.setBaseUrl("https://private-provider.example/v1");
        model.setApiKey("private-key");
        service.getModels().put("local", model);
        service.setDefaultModel("local");

        var options = service.getAvailableModelOptions();

        assertThat(options).containsExactly(new ModelOption("local", "Local model", true));
        assertThat(JsonMapper.builder().build().writeValueAsString(options))
                .doesNotContain("private-key", "private-provider", "provider-name");
    }

    @Test
    void promptsPreserveFormattingAndTrimSurroundingWhitespace() {
        service.getPrompts().put("clustering", new ByteArrayResource(
                "  Group examples\n\nReturn JSON: nozīme\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(service.getPrompt("clustering")).isEqualTo("Group examples\n\nReturn JSON: nozīme");
        assertThatThrownBy(() -> service.getPrompt("missing")).isInstanceOf(ConflictException.class);
    }
    @Test
    void unknownAndIncompleteModelsAreRejected() {
        assertThatThrownBy(() -> service.requestStructuredResponse(
                "clustering", "missing", null, Map.of(), ClusteredSenses.class))
                .isInstanceOf(ConflictException.class);

        service.getModels().put("incomplete", new ModelService.Model());
        assertThatThrownBy(() -> service.requestStructuredResponse(
                "clustering", "incomplete", null, Map.of(), ClusteredSenses.class))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void oversizedInputIsRejectedBeforeBuildingAnHttpRequest() {
        var model = new ModelService.Model();
        model.setBaseUrl("unused");
        model.setModel("local");
        service.getModels().put("local", model);
        service.getPrompts().put("clustering", new ByteArrayResource("prompt".getBytes(StandardCharsets.UTF_8)));
        service.setMaxInputCharacters(10);

        assertThatThrownBy(() -> service.requestStructuredResponse(
                "clustering", "local", null, Map.of("term", "a".repeat(11)), ClusteredSenses.class))
                .isInstanceOf(ConflictException.class).hasMessageContaining("10");
    }

}
