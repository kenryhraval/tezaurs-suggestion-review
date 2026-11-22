package lv.tezaurs.tip.sense.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.common.error.ApiExceptionHandler;
import lv.tezaurs.tip.sense.service.SenseClusteringService;
import lv.tezaurs.tip.sense.service.SenseDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SenseModelControllerTest {
    private final SenseClusteringService clustering = mock(SenseClusteringService.class);
    private final UUID reviewId = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.standaloneSetup(new SenseModelController(clustering, mock(SenseDefinitionService.class), new CurrentUserService()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void regroupingOnlyRequiresTheChosenModel() throws Exception {
        when(clustering.regroupSenses(eq(reviewId), any())).thenReturn(List.of());
        mvc.perform(post("/api/reviews/{id}/sense-research/regroup", reviewId)
                .header("Authorization", "Basic ZWRpdG9yOnBhc3M=").contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelId\":\"chosen-model\"}"))
                .andExpect(status().isOk());
        verify(clustering).regroupSenses(eq(reviewId), argThat(request -> request.modelId().equals("chosen-model")));
    }

    @Test
    void rejectsUnauthenticatedRegroupingBeforeCallingTheModel() throws Exception {
        mvc.perform(post("/api/reviews/{id}/sense-research/regroup", reviewId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(clustering);
    }
}
