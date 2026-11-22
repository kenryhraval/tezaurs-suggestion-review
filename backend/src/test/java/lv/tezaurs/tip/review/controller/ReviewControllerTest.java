package lv.tezaurs.tip.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lv.tezaurs.tip.review.dto.ReviewPageResponse;
import lv.tezaurs.tip.review.dto.ReviewResponse;
import lv.tezaurs.tip.review.dto.ReviewSort;
import lv.tezaurs.tip.review.dto.UpdateReviewTypeRequest;
import lv.tezaurs.tip.review.dto.UpdateStatusRequest;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewControllerTest {
    private ReviewService service;
    private ReviewController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewService.class);
        controller = new ReviewController(service);
    }

    @Test
    void listsAllStatusesWhenStatusFilterIsOmitted() throws Exception {
        when(service.list(null, null, "", 0, 20, ReviewSort.NEWEST))
                .thenReturn(new ReviewPageResponse(List.of(), 0, 0, 0));

        var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mockMvc.perform(get("/api/reviews")).andExpect(status().isOk());

        verify(service).list(null, null, "", 0, 20, ReviewSort.NEWEST);
    }

    @Test
    void acceptsMultipleReviewTypes() throws Exception {
        List<ReviewType> types = List.of(ReviewType.ENTRY, ReviewType.SENSE);
        when(service.list(null, types, "", 0, 20, ReviewSort.NEWEST))
                .thenReturn(new ReviewPageResponse(List.of(), 0, 0, 0));

        var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mockMvc.perform(get("/api/reviews").param("types", "ENTRY", "SENSE"))
                .andExpect(status().isOk());

        verify(service).list(null, types, "", 0, 20, ReviewSort.NEWEST);
    }

    @Test
    void looksUpAReviewByItsOwnId() {
        ReviewResponse response = response();
        ReviewPageResponse page = new ReviewPageResponse(List.of(), 0, 0, 0);
        UpdateStatusRequest request = new UpdateStatusRequest(ReviewStatus.IN_PROGRESS);
        when(service.findById(response.id())).thenReturn(response);
        when(service.list(ReviewStatus.READY_FOR_REVIEW, null, "term", 0, 20,
                ReviewSort.NEWEST)).thenReturn(page);
        when(service.changeStatus(response.id(), request, "Basic bWlrdXM6cHc=")).thenReturn(response);

        assertThat(controller.findById(response.id())).isEqualTo(response);
        assertThat(controller.list(ReviewStatus.READY_FOR_REVIEW, null, "term", 0, 20,
                ReviewSort.NEWEST)).isEqualTo(page);
        assertThat(controller.changeStatus(response.id(), request, "Basic bWlrdXM6cHc=")).isEqualTo(response);
    }

    @Test
    void detachesASuggestionFromTheSpecifiedReview() {
        ReviewResponse response = response();
        when(service.moveSuggestionToNewReview(response.id(), 42)).thenReturn(response);

        assertThat(controller.moveSuggestionToNewReview(response.id(), 42)).isEqualTo(response);
    }

    @Test
    void forwardsTheUserWhenRecordingADictionaryDecision() {
        ReviewResponse response = response();
        var request = new UpdateReviewTypeRequest(ReviewType.SENSE, 42, true);
        String authorization = "Basic bWlrdXM6cHc=";
        when(service.changeType(response.id(), request, authorization)).thenReturn(response);

        assertThat(controller.changeType(response.id(), request, authorization)).isEqualTo(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"decision\":null}"})
    void rejectsMissingUsageDecisions(String body) throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/reviews/{reviewId}/usage/decision", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private static ReviewResponse response() {
        return new ReviewResponse(UUID.randomUUID(), "k7m2pa", "term", ReviewStatus.IN_PROGRESS,
                "mikus",
                ReviewType.UNKNOWN, null, null, null, null, null,
                Instant.EPOCH, List.of());
    }
}
