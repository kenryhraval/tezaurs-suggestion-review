package lv.tezaurs.suggestions.suggestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewSort;
import lv.tezaurs.suggestions.suggestion.dto.ReviewPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.service.ReviewService;
import lv.tezaurs.suggestions.tezaurs.dto.TezaursEntryCandidateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewControllerTest {
    private ReviewService service;
    private ReviewController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReviewService.class);
        controller = new ReviewController(service);
    }

    @Test
    void looksUpAReviewByItsOwnId() {
        ReviewResponse response = response();
        ReviewPageResponse page = new ReviewPageResponse(List.of(), 0, 0, 0);
        UpdateStatusRequest request = new UpdateStatusRequest(ReviewStatus.IN_PROGRESS);
        when(service.find(response.id())).thenReturn(response);
        when(service.list(ReviewStatus.READY_FOR_REVIEW, "term", 0, 20,
                ReviewSort.NEWEST)).thenReturn(page);
        when(service.changeStatus(response.id(), request, "Basic bWlrdXM6cHc=")).thenReturn(response);

        assertThat(controller.find(response.id())).isEqualTo(response);
        assertThat(controller.list(ReviewStatus.READY_FOR_REVIEW, "term", 0, 20,
                ReviewSort.NEWEST)).isEqualTo(page);
        assertThat(controller.changeStatus(response.id(), request, "Basic bWlrdXM6cHc=")).isEqualTo(response);
    }

    @Test
    void delegatesTezaursCandidateSearch() {
        ReviewResponse review = response();
        TezaursEntryCandidateResponse candidate = new TezaursEntryCandidateResponse(
                42, "vārds", "vārds:1", List.of());
        when(service.findTezaursCandidates(review.id(), "cits")).thenReturn(List.of(candidate));

        assertThat(controller.findTezaursCandidates(review.id(), "cits")).containsExactly(candidate);
    }

    @Test
    void detachesASuggestionFromTheSpecifiedCase() {
        ReviewResponse response = response();
        when(service.detachSuggestion(response.id(), 42)).thenReturn(response);

        assertThat(controller.detachSuggestion(response.id(), 42)).isEqualTo(response);
    }

    @Test
    void forwardsTheUserWhenRecordingADictionaryDecision() {
        ReviewResponse response = response();
        var request = new RecordTezaursCheckRequest(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        String authorization = "Basic bWlrdXM6cHc=";
        when(service.recordTezaursCheck(response.id(), request, authorization)).thenReturn(response);

        assertThat(controller.recordTezaursCheck(response.id(), request, authorization)).isEqualTo(response);
    }

    private static ReviewResponse response() {
        return new ReviewResponse(UUID.randomUUID(), "term", ReviewStatus.IN_PROGRESS,
                "mikus",
                TezaursStatus.NOT_CHECKED, null, lv.tezaurs.suggestions.usage.entity.UsageDecision.NOT_CHECKED, null, null, null, false, CompletionBlockReason.TEZAURS_CHECK_REQUIRED,
                Instant.EPOCH, List.of());
    }
}
