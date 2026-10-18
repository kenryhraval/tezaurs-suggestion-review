package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionListItem;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
    void listsUnstartedSourcesForNewStatus() {
        IncubatorSuggestion unstartedSuggestion = source(41);
        when(sourceSuggestionRepository.findUnstartedForReviewQueue(eq("term"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unstartedSuggestion)));

        SuggestionPageResponse response = service.list(
                SuggestionStatus.NEW, " term ", 0, 20, SuggestionSort.NEWEST);

        assertThat(response.items()).extracting(SuggestionResponse::id).containsExactly(41);
        assertThat(response.items()).extracting(SuggestionResponse::status)
                .containsExactly(SuggestionStatus.NEW);
    }

    @Test
    void listsReviewsForTheSelectedStatus() {
        SuggestionListItem startedReview = item(42, SuggestionStatus.IN_PROGRESS);
        when(reviewRepository.findForReviewQueue(
                eq(SuggestionStatus.IN_PROGRESS), eq("term"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(startedReview)));

        SuggestionPageResponse response = service.list(
                SuggestionStatus.IN_PROGRESS, "term", 0, 20,
                SuggestionSort.MOST_SUGGESTIONS);

        assertThat(response.items()).extracting(SuggestionResponse::id).containsExactly(42);
        assertThat(response.items()).extracting(SuggestionResponse::status)
                .containsExactly(SuggestionStatus.IN_PROGRESS);
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
        when(item.getStatus()).thenReturn(status);
        when(item.getSourceCount()).thenReturn(3);
        return item;
    }
}
