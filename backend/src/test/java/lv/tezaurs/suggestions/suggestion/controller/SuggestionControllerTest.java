package lv.tezaurs.suggestions.suggestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SuggestionControllerTest {
    @Test
    void delegatesSourceSuggestionActions() {
        SuggestionService service = mock(SuggestionService.class);
        SuggestionController controller = new SuggestionController(service);
        SuggestionResponse response = new SuggestionResponse(42, "term", "definition", null,
                null, null, null, null, SuggestionStatus.NEW, Instant.EPOCH, 1);
        SuggestionPageResponse page = new SuggestionPageResponse(List.of(response), 0, 1, 1);
        UpdateStatusRequest request = new UpdateStatusRequest(SuggestionStatus.READY_FOR_REVIEW);
        when(service.list(SuggestionStatus.NEW, "", 0, 20, SuggestionSort.NEWEST))
                .thenReturn(page);
        when(service.changeStatus(response.id(), request)).thenReturn(response);

        assertThat(controller.list(SuggestionStatus.NEW, "", 0, 20, SuggestionSort.NEWEST))
                .isEqualTo(page);
        assertThat(controller.changeStatus(response.id(), request)).isEqualTo(response);
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        SuggestionController controller = new SuggestionController(mock(SuggestionService.class));
        var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/suggestions").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/suggestions").param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
