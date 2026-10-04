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
import lv.tezaurs.suggestions.suggestion.dto.CorrectTermRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.entity.CheckStatus;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.service.ReviewService;
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

        var found = controller.findBySourceSuggestionId(42);
        var missing = controller.findBySourceSuggestionId(999);

        assertThat(found.getBody()).isEqualTo(response);
        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(missing.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void delegatesReviewUpdates() {
        ReviewResponse response = response();
        CorrectTermRequest correction = new CorrectTermRequest("corrected");
        RecordTezaursCheckRequest check = new RecordTezaursCheckRequest(TezaursStatus.FOUND, 42L);
        when(service.correctTerm(response.id(), correction)).thenReturn(response);
        when(service.recordTezaursCheck(response.id(), check)).thenReturn(response);

        assertThat(controller.correctTerm(response.id(), correction)).isEqualTo(response);
        assertThat(controller.recordTezaursCheck(response.id(), check)).isEqualTo(response);
    }

    @Test
    void delegatesCorpusExampleActions() {
        ReviewResponse review = response();
        AddCorpusExampleRequest request = new AddCorpusExampleRequest("https://korpuss.lv/id/42");
        CorpusExampleResponse example = new CorpusExampleResponse(
                UUID.randomUUID(), review.id(), request.url(), Instant.EPOCH);
        when(service.listCorpusExamples(review.id())).thenReturn(List.of(example));
        when(service.addCorpusExample(review.id(), request)).thenReturn(example);

        assertThat(controller.listCorpusExamples(review.id())).containsExactly(example);
        var result = controller.addCorpusExample(review.id(), request);
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getHeaders().getLocation()).hasToString(
                "/api/reviews/" + review.id() + "/corpus-examples/" + example.id());
    }

    private static ReviewResponse response() {
        return new ReviewResponse(UUID.randomUUID(), 42, null, TezaursStatus.NOT_CHECKED, null,
                CheckStatus.NOT_CHECKED, false, CompletionBlockReason.TEZAURS_CHECK_REQUIRED,
                Instant.EPOCH, Instant.EPOCH);
    }
}
