package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import lv.tezaurs.suggestions.suggestion.entity.CaseSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.suggestion.repository.CaseSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionImportRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionImportRepository.SourceSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class ReviewCaseImportServiceTest {
    private final SuggestionImportRepository importRepository = mock(SuggestionImportRepository.class);
    private final IncubatorSuggestionRepository sourceRepository = mock(IncubatorSuggestionRepository.class);
    private final ReviewCaseRepository caseRepository = mock(ReviewCaseRepository.class);
    private final CaseSuggestionRepository linkRepository = mock(CaseSuggestionRepository.class);
    private final ReviewCaseImportService service = new ReviewCaseImportService(
            importRepository, sourceRepository, caseRepository, linkRepository);

    @BeforeEach
    void setUp() {
        when(importRepository.tryAcquireImportLock()).thenReturn(true);
        when(caseRepository.save(any(ReviewCase.class))).thenAnswer(call -> call.getArgument(0));
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
        IncubatorSuggestion source = new IncubatorSuggestion(42, "vārds", "skaidrojums");
        when(sourceRepository.getReferenceById(42)).thenReturn(source);

        assertThat(service.createMissingCases()).isEqualTo(1);

        var savedLink = ArgumentCaptor.forClass(CaseSuggestion.class);
        verify(linkRepository).save(savedLink.capture());
        CaseSuggestion link = savedLink.getValue();
        ReviewCase review = link.getReviewCase();
        assertThat(link.getSourceSuggestion()).isSameAs(source);
        assertThat(review.getId()).isNotNull();
        assertThat(review.getReviewedTerm()).isEqualTo("vārds");
        assertThat(review.getStatus()).isEqualTo(expectedStatus);
        assertThat(review.getLastStatusChangedBy()).isNull();
        assertThat(review.getType()).isEqualTo(CaseType.UNKNOWN);
        assertThat(review.getMatchedEntryId()).isNull();
    }

    @Test
    void preservesSubmittedTermsLongerThan255Characters() {
        String submittedTerm = "Garš iesniegtais teksts. ".repeat(20);
        when(importRepository.findUnlinkedSuggestions()).thenReturn(
                List.of(new SourceSuggestion(42, submittedTerm, 0)));
        when(sourceRepository.getReferenceById(42)).thenReturn(
                new IncubatorSuggestion(42, submittedTerm, "skaidrojums"));

        assertThat(service.createMissingCases()).isEqualTo(1);

        var savedCase = ArgumentCaptor.forClass(ReviewCase.class);
        verify(caseRepository).save(savedCase.capture());
        assertThat(savedCase.getValue().getReviewedTerm()).isEqualTo(submittedTerm);
    }

    @Test
    void skipsImportWhenAnotherInstanceHoldsTheLock() {
        when(importRepository.tryAcquireImportLock()).thenReturn(false);

        assertThat(service.createMissingCases()).isZero();

        verifyNoInteractions(sourceRepository, caseRepository, linkRepository);
    }

    @Test
    void doesNotCreateCasesWhenEverySuggestionIsAlreadyLinked() {
        when(importRepository.findUnlinkedSuggestions()).thenReturn(List.of());

        assertThat(service.createMissingCases()).isZero();

        verifyNoInteractions(sourceRepository, caseRepository, linkRepository);
    }

    @Test
    void importedInProgressCasesCanBeClaimedWithoutReplacingAnExistingReviewer() {
        ReviewCase review = ReviewCase.fromSuggestion("vārds", ReviewStatus.IN_PROGRESS);

        review.takeForReview("mikus");
        review.takeForReview("laura");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void scheduledJobDelegatesToTheTransactionalImporter() {
        ReviewCaseImportService importer = mock(ReviewCaseImportService.class);
        when(importer.createMissingCases()).thenReturn(3, 0);
        ReviewCaseImportJob job = new ReviewCaseImportJob(importer);

        job.importSuggestions();
        job.importSuggestions();

        verify(importer, org.mockito.Mockito.times(2)).createMissingCases();
    }
}
