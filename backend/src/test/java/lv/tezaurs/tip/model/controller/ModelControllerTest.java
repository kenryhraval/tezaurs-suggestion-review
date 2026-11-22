package lv.tezaurs.tip.model.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lv.tezaurs.tip.model.service.ModelService;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelControllerTest {
    @Test
    void exposesOnlyModelIdsLabelsAndDefaultSelection() throws Exception {
        var settings = new ModelService();
        var model = new ModelService.Model();
        model.setLabel("Test model");
        model.setBaseUrl("https://private-provider.example/v1");
        model.setModel("provider-model-name");
        model.setApiKey("private-api-key");
        settings.setDefaultModel("test");
        settings.getModels().put("test", model);
        var mvc = MockMvcBuilders.standaloneSetup(new ModelController(settings)).build();

        var response = mvc.perform(get("/api/sense-models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("test"))
                .andExpect(jsonPath("$[0].label").value("Test model"))
                .andExpect(jsonPath("$[0].defaultModel").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("private-api-key", "private-provider", "provider-model-name", "apiKey", "baseUrl");
    }
    @Test
    void exposesTheCompletePromptAsText() throws Exception {
        var settings = new ModelService();
        settings.getPrompts().put("clustering", new ByteArrayResource("Group examples\n\nReturn JSON: nozīme".getBytes(StandardCharsets.UTF_8)));
        var mvc = MockMvcBuilders.standaloneSetup(new ModelController(settings)).build();

        mvc.perform(get("/api/sense-models/prompts/clustering"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string("Group examples\n\nReturn JSON: nozīme"));
    }
}
