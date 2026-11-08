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
import lv.tezaurs.suggestions.suggestion.dto.ReviewResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionPageResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionResponse;
import lv.tezaurs.suggestions.suggestion.dto.SuggestionSort;
import lv.tezaurs.suggestions.suggestion.dto.UpdateStatusRequest;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class SuggestionServiceTest {
    private IncubatorSuggestionRepository sourceSuggestionRepository;
    private ReviewService reviewService;
    private ReviewCaseRepository caseRepository;
    private SuggestionService service;

    @BeforeEach
    void setUp() {
        sourceSuggestionRepository = mock(IncubatorSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewService = mock(ReviewService.class);
        caseRepository = mock(ReviewCaseRepository.class);
        service = new SuggestionService(new ReviewCompletionService(
                mock(lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository.class),
                mock(lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository.class),
                mock(lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository.class)),
                sourceSuggestionRepository, reviewService, caseRepository);
    }

    @Test
    void countsAllSourcesAndCasesOutsideTheTrash() {
        when(sourceSuggestionRepository.count()).thenReturn(3727L);
        when(caseRepository.countByStatusNot(ReviewStatus.GARBAGE)).thenReturn(15L);

        var counts = service.counts();

        assertThat(counts.suggestions()).isEqualTo(3727L);
        assertThat(counts.cases()).isEqualTo(15L);
    }

    @Test
    void listsUnstartedSourcesForNewStatus() {
        IncubatorSuggestion unstartedSuggestion = source(41);
        when(sourceSuggestionRepository.findUnstartedForReviewQueue(eq("term"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unstartedSuggestion)));

        SuggestionPageResponse response = service.list(
                " term ", 0, 20, SuggestionSort.NEWEST);

        assertThat(response.items()).extracting(SuggestionResponse::id).containsExactly(41);
    }

    @Test
    void delegatesTheFirstStatusChangeToReviewCreation() {
        IncubatorSuggestion source = source(42);
        ReviewCase review = new ReviewCase("term", "reviewer");
        review.changeStatus(ReviewStatus.GARBAGE, "reviewer");
        when(sourceSuggestionRepository.findById(42)).thenReturn(Optional.of(source));
        when(reviewService.startReview(source, ReviewStatus.GARBAGE, null)).thenReturn(review);

        ReviewResponse response = service.changeStatus(42,
                new UpdateStatusRequest(ReviewStatus.GARBAGE), null);

        assertThat(response.status()).isEqualTo(ReviewStatus.GARBAGE);
        verify(reviewService).startReview(source, ReviewStatus.GARBAGE, null);
    }

    private static IncubatorSuggestion source(int id) {
        return new IncubatorSuggestion(id, "term", "definition");
    }
}
