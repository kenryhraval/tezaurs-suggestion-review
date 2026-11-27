package lv.tezaurs.tip.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewSuggestion;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.review.service.ReviewService;
import lv.tezaurs.tip.jobs.SuggestionImportJob;
import lv.tezaurs.tip.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.tip.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.tip.suggestion.repository.SuggestionImportRepository.SourceSuggestion;
import lv.tezaurs.tip.suggestion.repository.SuggestionImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class SuggestionServiceTest {
    private final SuggestionImportRepository importRepository = mock(SuggestionImportRepository.class);
    private final IncubatorSuggestionRepository suggestionRepository = mock(IncubatorSuggestionRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final ReviewSuggestionRepository linkRepository = mock(ReviewSuggestionRepository.class);
    private final ReviewService reviewService = mock(ReviewService.class);
    private final SuggestionService service = new SuggestionService(
            importRepository, suggestionRepository, reviewRepository, linkRepository, reviewService);

    @BeforeEach
    void setUp() {
        when(importRepository.tryAcquireImportLock()).thenReturn(true);
        when(reviewService.createFromSuggestion(any(String.class), any(ReviewStatus.class)))
                .thenAnswer(call -> new Review(call.getArgument(0, String.class), call.getArgument(1, ReviewStatus.class), "case01"));
    }

    @Test
    void countsSuggestionsButExcludesNewAndTrashFromReviewCount() {
        when(suggestionRepository.count()).thenReturn(3727L);
        when(reviewRepository.countByStatusNotIn(List.of(ReviewStatus.NEW, ReviewStatus.GARBAGE))).thenReturn(2996L);

        var counts = service.counts();

        assertThat(counts.suggestions()).isEqualTo(3727L);
        assertThat(counts.cases()).isEqualTo(2996L);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,NEW", "1,READY_FOR_REVIEW", "2,GARBAGE", "3,COMPLETED", "4,IN_PROGRESS",
            "5,INVENTED", "6,ALREADY_EXISTS", "7,INSUFFICIENT_DATA", "8,NEEDS_EXPERT",
            "NULL,NEW", "83,NEW", "-1,NEW"
    }, nullValues = "NULL")
    void importsLegacyStatusWithoutInventingAnAuthorOrChecks(Integer sourceStatus, ReviewStatus expectedStatus) {
        when(importRepository.findUnlinkedSuggestions()).thenReturn(
                List.of(new SourceSuggestion(42, "vārds", sourceStatus)));
        IncubatorSuggestion suggestion = new IncubatorSuggestion(42, "vārds", "skaidrojums");
        when(suggestionRepository.getReferenceById(42)).thenReturn(suggestion);

        assertThat(service.importSuggestions()).isEqualTo(1);

        var savedLink = ArgumentCaptor.forClass(ReviewSuggestion.class);
        verify(linkRepository).save(savedLink.capture());
        ReviewSuggestion link = savedLink.getValue();
        Review review = link.getReview();
        assertThat(link.getSourceSuggestion()).isSameAs(suggestion);
        assertThat(review.getId()).isNotNull();
        assertThat(review.getPublicId()).isEqualTo("case01");
        verify(reviewService).createFromSuggestion("vārds", expectedStatus);
        assertThat(review.getReviewedTerm()).isEqualTo("vārds");
        assertThat(review.getStatus()).isEqualTo(expectedStatus);
        assertThat(review.getLastStatusChangedBy()).isNull();
        assertThat(review.getType()).isEqualTo(ReviewType.UNKNOWN);
        assertThat(review.getMatchedEntryId()).isNull();
    }

    @Test
    void preservesSubmittedTermsLongerThan255Characters() {
        String submittedTerm = "Garš iesniegtais teksts. ".repeat(20);
        when(importRepository.findUnlinkedSuggestions()).thenReturn(
                List.of(new SourceSuggestion(42, submittedTerm, 0)));
        when(suggestionRepository.getReferenceById(42)).thenReturn(
                new IncubatorSuggestion(42, submittedTerm, "skaidrojums"));

        assertThat(service.importSuggestions()).isEqualTo(1);

        verify(reviewService).createFromSuggestion(submittedTerm, ReviewStatus.NEW);
    }

    @Test
    void skipsImportWhenAnotherInstanceHoldsTheLock() {
        when(importRepository.tryAcquireImportLock()).thenReturn(false);

        assertThat(service.importSuggestions()).isZero();

        verifyNoInteractions(suggestionRepository, reviewRepository, linkRepository, reviewService);
    }

    @Test
    void doesNotCreateReviewsWhenEverySuggestionIsAlreadyLinked() {
        when(importRepository.findUnlinkedSuggestions()).thenReturn(List.of());

        assertThat(service.importSuggestions()).isZero();

        verifyNoInteractions(suggestionRepository, reviewRepository, linkRepository, reviewService);
    }

    @Test
    void scheduledJobDelegatesToTheTransactionalImporter() {
        SuggestionService importer = mock(SuggestionService.class);
        when(importer.importSuggestions()).thenReturn(3, 0);
        SuggestionImportJob job = new SuggestionImportJob(importer);

        job.importSuggestions();
        job.importSuggestions();

        verify(importer, org.mockito.Mockito.times(2)).importSuggestions();
    }
}
