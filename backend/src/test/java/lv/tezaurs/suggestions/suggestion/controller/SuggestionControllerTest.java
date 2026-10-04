package lv.tezaurs.suggestions.suggestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.service.SuggestionService;
import org.junit.jupiter.api.Test;

class SuggestionControllerTest {

    @Test
    void delegatesSourceSuggestionActions() {
        SuggestionService service = mock(SuggestionService.class);
        SuggestionController controller = new SuggestionController(service);
        SuggestionResponse response = response();
        UpdateStatusRequest request = new UpdateStatusRequest(SuggestionStatus.READY_FOR_REVIEW);
        when(service.list()).thenReturn(List.of(response));
        when(service.changeStatus(response.id(), request)).thenReturn(response);

        assertThat(controller.list()).containsExactly(response);
        assertThat(controller.changeStatus(response.id(), request)).isEqualTo(response);
    }

    private static SuggestionResponse response() {
        return new SuggestionResponse(42, "term", "definition", null, null, null, null, null,
                SuggestionStatus.NEW, Instant.EPOCH, null);
    }
}
