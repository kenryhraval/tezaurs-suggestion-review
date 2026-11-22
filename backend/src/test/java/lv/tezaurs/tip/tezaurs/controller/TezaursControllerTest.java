package lv.tezaurs.tip.tezaurs.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lv.tezaurs.tip.tezaurs.dto.TezaursEntryCandidateResponse;
import lv.tezaurs.tip.tezaurs.service.TezaursEntrySearchService;
import lv.tezaurs.tip.tezaurs.service.TezaursService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TezaursControllerTest {
    private final TezaursService service = mock(TezaursService.class);
    private final TezaursEntrySearchService searchService = mock(TezaursEntrySearchService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TezaursController(service, searchService)).build();

    @Test
    void searchesDictionaryEntriesWithoutAReview() throws Exception {
        var entry = new TezaursEntryCandidateResponse(42, "lemt", "lemt:1", List.of());
        when(searchService.findMatches("lemt")).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/tezaurs/entries").param("query", "lemt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].heading").value("lemt"));

        verify(searchService).findMatches("lemt");
        verifyNoInteractions(service);
    }

    @Test
    void rejectsSearchesWithoutAQueryParameter() throws Exception {
        mockMvc.perform(get("/api/tezaurs/entries"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchService, service);
    }
}
