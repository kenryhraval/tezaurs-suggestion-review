package lv.tezaurs.tip.example.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import lv.tezaurs.tip.common.error.ApiExceptionHandler;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.corpus.service.CorpusService;
import lv.tezaurs.tip.example.dto.SelectCorpusExamplesRequest;
import lv.tezaurs.tip.example.service.ExampleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExampleControllerTest {
    private final CorpusService selection = mock(CorpusService.class);
    private final UUID reviewId = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.standaloneSetup(new ExampleController(mock(ExampleService.class), selection))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void delegatesRandomSelectionAsOneRequest() throws Exception {
        var request = new SelectCorpusExamplesRequest(Corpus.LVK2022, "vārds", true, 5);
        when(selection.selectAndSaveCorpusExamples(reviewId, request, "Basic test")).thenReturn(List.of());
        mvc.perform(post("/api/reviews/{id}/usage/examples/select", reviewId)
                .header("Authorization", "Basic test").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"corpus":"LVK2022","query":"vārds","exactOnly":true,"count":5}
                        """))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(selection).selectAndSaveCorpusExamples(reviewId, request, "Basic test");
    }

    @Test
    void omittingTheCountSelectsAll() throws Exception {
        mvc.perform(post("/api/reviews/{id}/usage/examples/select", reviewId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"corpus":"LVK2022","query":"vārds","exactOnly":false}
                        """))
                .andExpect(status().isOk());
        verify(selection).selectAndSaveCorpusExamples(reviewId, new SelectCorpusExamplesRequest(Corpus.LVK2022, "vārds", false, null), null);
    }

    @Test
    void rejectsInvalidSelectionBeforeFetchingAnything() throws Exception {
        for (String body : List.of(
                "{\"corpus\":\"LVK2022\",\"query\":\"vārds\",\"exactOnly\":false,\"count\":0}",
                "{\"corpus\":\"LVK2022\",\"query\":\" \",\"exactOnly\":false}",
                "{\"query\":\"vārds\",\"exactOnly\":false}")) {
            mvc.perform(post("/api/reviews/{id}/usage/examples/select", reviewId)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(selection);
    }
}
