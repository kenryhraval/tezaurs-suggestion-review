package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionListItem;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class SuggestionServiceTest {
    private IncubatorSuggestionRepository sourceSuggestionRepository;
    private ReviewSuggestionRepository reviewRepository;
    private ReviewService reviewService;
    private SuggestionService service;

    @BeforeEach
    void setUp() {
        sourceSuggestionRepository = mock(IncubatorSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewRepository = mock(ReviewSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewService = mock(ReviewService.class);
        service = new SuggestionService(sourceSuggestionRepository, reviewRepository, reviewService);
    }

    @Test
    void listsUnstartedSourcesAndStartedReviewsOnceEach() {
        IncubatorSuggestion unstartedSuggestion = source(41);
        SuggestionListItem startedReview = item(42, SuggestionStatus.IN_PROGRESS);
        when(sourceSuggestionRepository.findAllByReviewSourceIsNullOrderByCreatedAtAscIdAsc())
                .thenReturn(List.of(unstartedSuggestion));
        when(reviewRepository.findForReviewQueue(null)).thenReturn(List.of(startedReview));

        List<SuggestionResponse> responses = service.list(null);

        assertThat(responses).extracting(SuggestionResponse::id).containsExactly(41, 42);
        assertThat(responses).extracting(SuggestionResponse::status)
                .containsExactly(SuggestionStatus.NEW, SuggestionStatus.IN_PROGRESS);
    }

    @Test
    void listsOnlyUnstartedSourcesForNewStatus() {
        IncubatorSuggestion unstartedSuggestion = source(41);
        when(sourceSuggestionRepository.findAllByReviewSourceIsNullOrderByCreatedAtAscIdAsc())
                .thenReturn(List.of(unstartedSuggestion));

        List<SuggestionResponse> responses = service.list(SuggestionStatus.NEW);

        assertThat(responses).extracting(SuggestionResponse::id).containsExactly(41);
    }

    @Test
    void listsOnlyReviewsForOtherStatuses() {
        SuggestionListItem startedReview = item(42, SuggestionStatus.IN_PROGRESS);
        when(reviewRepository.findForReviewQueue(SuggestionStatus.IN_PROGRESS.name()))
                .thenReturn(List.of(startedReview));

        List<SuggestionResponse> responses = service.list(SuggestionStatus.IN_PROGRESS);

        assertThat(responses).extracting(SuggestionResponse::id).containsExactly(42);
    }

    @Test
    void delegatesTheFirstStatusChangeToReviewCreation() {
        IncubatorSuggestion source = source(42);
        ReviewSuggestion review = new ReviewSuggestion("term");
        review.changeStatus(SuggestionStatus.GARBAGE);
        when(sourceSuggestionRepository.findById(42)).thenReturn(Optional.of(source));
        when(reviewService.changeStatus(source, SuggestionStatus.GARBAGE)).thenReturn(review);

        SuggestionResponse response = service.changeStatus(42,
                new UpdateStatusRequest(SuggestionStatus.GARBAGE));

        assertThat(response.status()).isEqualTo(SuggestionStatus.GARBAGE);
        verify(reviewService).changeStatus(source, SuggestionStatus.GARBAGE);
    }

    private static IncubatorSuggestion source(int id) {
        return new IncubatorSuggestion(id, "term", "definition");
    }

    private static SuggestionListItem item(int id, SuggestionStatus status) {
        SuggestionListItem item = mock(SuggestionListItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getStatus()).thenReturn(status.name());
        return item;
    }
}
