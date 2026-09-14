package lv.tezaurs.suggestions.meaning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRequest;
import lv.tezaurs.suggestions.meaning.dto.CreateMeaningRevisionRequest;
import lv.tezaurs.suggestions.meaning.dto.MeaningResponse;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.entity.MeaningStatus;
import lv.tezaurs.suggestions.meaning.service.MeaningService;
import org.junit.jupiter.api.Test;

class MeaningControllerTest {
    @Test
    void delegatesMeaningActions() {
        MeaningService service = mock(MeaningService.class);
        MeaningController controller = new MeaningController(service);
        UUID suggestionId = UUID.randomUUID();
        UUID meaningId = UUID.randomUUID();
        MeaningResponse response = new MeaningResponse(meaningId, suggestionId, null, null, MeaningOrigin.REVIEWER,
                "gloss", MeaningStatus.PROPOSED, Instant.EPOCH, Instant.EPOCH);
        CreateMeaningRequest createRequest = new CreateMeaningRequest("gloss", null);
        CreateMeaningRevisionRequest revisionRequest = new CreateMeaningRevisionRequest("revision");
        when(service.list(suggestionId)).thenReturn(List.of(response));
        when(service.create(suggestionId, createRequest)).thenReturn(response);
        when(service.revise(suggestionId, meaningId, revisionRequest)).thenReturn(response);
        when(service.approve(suggestionId, meaningId)).thenReturn(response);
        when(service.reject(suggestionId, meaningId)).thenReturn(response);

        assertThat(controller.list(suggestionId)).containsExactly(response);
        assertThat(controller.create(suggestionId, createRequest).getStatusCode().value()).isEqualTo(201);
        assertThat(controller.revise(suggestionId, meaningId, revisionRequest).getStatusCode().value()).isEqualTo(201);
        assertThat(controller.approve(suggestionId, meaningId)).isEqualTo(response);
        assertThat(controller.reject(suggestionId, meaningId)).isEqualTo(response);
    }
}
