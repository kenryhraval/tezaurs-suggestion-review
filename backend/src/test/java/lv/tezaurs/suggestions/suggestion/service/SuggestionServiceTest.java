package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class SuggestionServiceTest {
    private IncubatorSuggestionRepository repository;
    private ReviewService reviewService;
    private SuggestionService service;

    @BeforeEach
    void setUp() {
        repository = mock(IncubatorSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewService = mock(ReviewService.class);
        service = new SuggestionService(repository, reviewService);
    }

    @Test
    void listsOnlyIncubatorSuggestions() {
        IncubatorSuggestion first = source(41, SuggestionStatus.NEW);
        IncubatorSuggestion second = source(42, SuggestionStatus.READY_FOR_REVIEW);
        when(repository.findAllByChannelIdOrderByCreatedAtAscIdAsc(1)).thenReturn(List.of(first, second));

        assertThat(service.list()).extracting(SuggestionResponse::id).containsExactly(41, 42);
        verifyNoInteractions(reviewService);
    }

    @Test
    void createsAReviewWhenTheWorkflowFirstNeedsOne() {
        IncubatorSuggestion source = source(42, SuggestionStatus.NEW);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(source));

        SuggestionResponse response = service.changeStatus(42,
                new UpdateStatusRequest(SuggestionStatus.IN_PROGRESS));

        assertThat(response.status()).isEqualTo(SuggestionStatus.IN_PROGRESS);
        verify(reviewService).ensureCreated(source);
        verify(repository).flush();
    }

    @Test
    void doesNotCreateAReviewForGarbage() {
        IncubatorSuggestion source = source(42, SuggestionStatus.NEW);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(source));

        service.changeStatus(42, new UpdateStatusRequest(SuggestionStatus.GARBAGE));

        verify(reviewService, never()).ensureCreated(source);
    }

    @Test
    void rejectsAStatusTransitionOutsideTheTezaursGraph() {
        IncubatorSuggestion source = source(42, SuggestionStatus.READY_FOR_REVIEW);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.changeStatus(42,
                new UpdateStatusRequest(SuggestionStatus.GARBAGE)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Suggestion status cannot change from READY_FOR_REVIEW to GARBAGE");
    }

    private static IncubatorSuggestion source(int id, SuggestionStatus status) {
        return new IncubatorSuggestion(id, "term", "definition", status);
    }
}
