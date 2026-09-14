package lv.tezaurs.suggestions.suggestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.CreateSuggestionRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordCorpusCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.service.SuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuggestionControllerTest {
    private SuggestionService service;
    private SuggestionController controller;

    @BeforeEach
    void setUp() {
        service = mock(SuggestionService.class);
        controller = new SuggestionController(service);
    }

    @Test
    void createsASuggestionAtItsResourceLocation() {
        CreateSuggestionRequest request = new CreateSuggestionRequest("term", "definition", null, null, null, null);
        SuggestionResponse response = response();
        when(service.create(request)).thenReturn(response);

        var result = controller.create(request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getHeaders().getLocation()).hasToString("/api/suggestions/" + response.id());
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void delegatesSuggestionQueries() {
        SuggestionResponse response = response();
        when(service.list()).thenReturn(List.of(response));
        when(service.get(response.id())).thenReturn(response);

        assertThat(controller.list()).containsExactly(response);
        assertThat(controller.get(response.id())).isEqualTo(response);
    }

    @Test
    void delegatesReviewActions() {
        SuggestionResponse response = response();
        UpdateStatusRequest status = new UpdateStatusRequest(SuggestionStatus.IN_PROGRESS);
        CorrectTermRequest correction = new CorrectTermRequest("corrected");
        RecordTezaursCheckRequest tezaursCheck = new RecordTezaursCheckRequest(CheckStatus.FOUND, 42L);
        RecordCorpusCheckRequest corpusCheck = new RecordCorpusCheckRequest(CheckStatus.NOT_FOUND);
        when(service.changeStatus(response.id(), status)).thenReturn(response);
        when(service.correctTerm(response.id(), correction)).thenReturn(response);
        when(service.recordTezaursCheck(response.id(), tezaursCheck)).thenReturn(response);
        when(service.recordCorpusCheck(response.id(), corpusCheck)).thenReturn(response);

        assertThat(controller.changeStatus(response.id(), status)).isEqualTo(response);
        assertThat(controller.correctTerm(response.id(), correction)).isEqualTo(response);
        assertThat(controller.recordTezaursCheck(response.id(), tezaursCheck)).isEqualTo(response);
        assertThat(controller.recordCorpusCheck(response.id(), corpusCheck)).isEqualTo(response);
    }

    private static SuggestionResponse response() {
        return new SuggestionResponse(UUID.randomUUID(), "term", null, null, null, null, null,
                SuggestionStatus.NEW, CheckStatus.NOT_CHECKED, null, CheckStatus.NOT_CHECKED,
                Instant.EPOCH, Instant.EPOCH);
    }
}
