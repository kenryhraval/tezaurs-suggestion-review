package lv.tezaurs.suggestions.meaning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
        UUID reviewId = UUID.randomUUID();
        UUID meaningId = UUID.randomUUID();
        MeaningResponse response = new MeaningResponse(meaningId, reviewId, null, MeaningOrigin.REVIEWER,
                "gloss", MeaningStatus.CURRENT, Instant.EPOCH, Instant.EPOCH);
        CreateMeaningRevisionRequest revisionRequest = new CreateMeaningRevisionRequest("revision");
        when(service.list(reviewId)).thenReturn(List.of(response));
        when(service.revise(reviewId, meaningId, revisionRequest)).thenReturn(response);

        assertThat(controller.list(reviewId)).containsExactly(response);
        assertThat(controller.revise(reviewId, meaningId, revisionRequest).getStatusCode().value()).isEqualTo(201);
    }
}
