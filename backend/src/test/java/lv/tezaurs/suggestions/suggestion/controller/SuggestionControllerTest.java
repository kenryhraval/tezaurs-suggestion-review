package lv.tezaurs.suggestions.suggestion.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lv.tezaurs.suggestions.suggestion.dto.SuggestionCountsResponse;
import lv.tezaurs.suggestions.suggestion.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SuggestionControllerTest {
    @Test
    void exposesHeaderCounts() throws Exception {
        SuggestionService service = mock(SuggestionService.class);
        when(service.counts()).thenReturn(new SuggestionCountsResponse(3727L, 15L));
        var mockMvc = MockMvcBuilders.standaloneSetup(new SuggestionController(service)).build();

        mockMvc.perform(get("/api/suggestions/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").value(3727))
                .andExpect(jsonPath("$.cases").value(15));
    }

}
