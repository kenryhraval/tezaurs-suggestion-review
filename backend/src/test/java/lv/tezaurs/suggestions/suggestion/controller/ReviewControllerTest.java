package lv.tezaurs.suggestions.suggestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.dto.AddCorpusExampleRequest;
import lv.tezaurs.suggestions.suggestion.dto.CorpusExampleResponse;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
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
    void looksUpAReviewByItsSourceSuggestion() {
        ReviewResponse response = response();
        when(service.findBySourceSuggestionId(42)).thenReturn(Optional.of(response));

        assertThat(controller.findBySourceSuggestionId(42).getBody()).isEqualTo(response);
        assertThat(controller.findBySourceSuggestionId(999).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void delegatesCorpusAndTezaursCandidateActions() {
        ReviewResponse review = response();
        AddCorpusExampleRequest request = new AddCorpusExampleRequest("https://korpuss.lv/id/42");
        CorpusExampleResponse example = new CorpusExampleResponse(
                UUID.randomUUID(), review.id(), request.url(), Instant.EPOCH);
        TezaursEntryCandidateResponse candidate = new TezaursEntryCandidateResponse(
                42, "vārds", "vārds:1", "vārds", List.of());
        when(service.listCorpusExamples(review.id())).thenReturn(List.of(example));
        when(service.addCorpusExample(review.id(), request)).thenReturn(example);
        when(service.findTezaursCandidates(review.id(), "cits")).thenReturn(List.of(candidate));

        assertThat(controller.listCorpusExamples(review.id())).containsExactly(example);
        assertThat(controller.addCorpusExample(review.id(), request).getStatusCode().value()).isEqualTo(201);
        assertThat(controller.findTezaursCandidates(review.id(), "cits")).containsExactly(candidate);
    }

    private static ReviewResponse response() {
        return new ReviewResponse(UUID.randomUUID(), "term", TezaursStatus.NOT_CHECKED, null,
                CheckStatus.NOT_CHECKED, false, CompletionBlockReason.TEZAURS_CHECK_REQUIRED,
                Instant.EPOCH, Instant.EPOCH);
    }
}
