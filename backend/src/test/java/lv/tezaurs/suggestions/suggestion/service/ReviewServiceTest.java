package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Optional;
import java.util.List;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.meaning.entity.ReviewMeaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.repository.ReviewMeaningRepository;
import lv.tezaurs.suggestions.suggestion.dto.AddCorpusExampleRequest;
import lv.tezaurs.suggestions.suggestion.dto.RecordTezaursCheckRequest;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCorpusExample;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.ReviewSuggestionSource;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCorpusExampleRepository;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.ReviewSuggestionSourceRepository;
import lv.tezaurs.suggestions.tezaurs.repository.TezaursEntryRepository;
import lv.tezaurs.suggestions.tezaurs.service.TezaursEntrySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.data.domain.Pageable;

class ReviewServiceTest {
    private IncubatorSuggestionRepository sourceSuggestionRepository;
    private ReviewSuggestionRepository reviewRepository;
    private ReviewSuggestionSourceRepository reviewSourceRepository;
    private ReviewMeaningRepository meaningRepository;
    private ReviewCorpusExampleRepository corpusExampleRepository;
    private TezaursEntryRepository tezaursEntryRepository;
    private TezaursEntrySearchService tezaursEntrySearchService;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        sourceSuggestionRepository = mock(IncubatorSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewRepository = mock(ReviewSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewSourceRepository = mock(ReviewSuggestionSourceRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        meaningRepository = mock(ReviewMeaningRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        corpusExampleRepository = mock(ReviewCorpusExampleRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        tezaursEntryRepository = mock(TezaursEntryRepository.class);
        tezaursEntrySearchService = mock(TezaursEntrySearchService.class);
        service = new ReviewService(sourceSuggestionRepository, reviewRepository, reviewSourceRepository,
                meaningRepository,
                corpusExampleRepository, tezaursEntryRepository, tezaursEntrySearchService);
        when(reviewRepository.save(any(ReviewSuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewSourceRepository.save(any(ReviewSuggestionSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(meaningRepository.save(any(ReviewMeaning.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(corpusExampleRepository.saveAndFlush(any(ReviewCorpusExample.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void openingAnUnstartedSuggestionDoesNotCreateAReview() {
        IncubatorSuggestion source = source();
        when(sourceSuggestionRepository.findById(42)).thenReturn(Optional.of(source));

        assertThat(service.findBySourceSuggestionId(42)).isEmpty();
        verify(reviewRepository, never()).save(any(ReviewSuggestion.class));
    }

    @Test
    void theFirstStatusChangeCreatesTheLinkedReviewAndInitialMeaning() {
        IncubatorSuggestion source = source();

        ReviewSuggestion review = service.changeStatus(source, SuggestionStatus.READY_FOR_REVIEW);

        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.READY_FOR_REVIEW);
        ArgumentCaptor<ReviewSuggestionSource> link = ArgumentCaptor.forClass(ReviewSuggestionSource.class);
        verify(reviewSourceRepository).save(link.capture());
        assertThat(link.getValue().getSourceSuggestion()).isSameAs(source);
        assertThat(link.getValue().getReview()).isSameAs(review);
        assertThat(link.getValue().isPrimarySource()).isTrue();
        ArgumentCaptor<ReviewMeaning> meaning = ArgumentCaptor.forClass(ReviewMeaning.class);
        verify(meaningRepository).save(meaning.capture());
        assertThat(meaning.getValue().getReviewId()).isEqualTo(review.getId());
        assertThat(meaning.getValue().getOrigin()).isEqualTo(MeaningOrigin.SUBMITTER);
    }

    @Test
    void findsExistingReviewsAndLinksDuplicateSources() {
        IncubatorSuggestion source = source();
        ReviewSuggestion review = new ReviewSuggestion("term");
        review.changeStatus(SuggestionStatus.READY_FOR_REVIEW);
        when(reviewRepository.findByReviewedTermContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("term"), any(Pageable.class)))
                .thenReturn(List.of(review));
        when(reviewSourceRepository.countByReviewId(review.getId())).thenReturn(2L);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var candidates = service.findDuplicateCandidates("  term  ");
        service.linkDuplicate(source, review.getId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().sourceCount()).isEqualTo(2);
        ArgumentCaptor<ReviewSuggestionSource> link = ArgumentCaptor.forClass(ReviewSuggestionSource.class);
        verify(reviewSourceRepository).save(link.capture());
        assertThat(link.getValue().getSourceSuggestion()).isSameAs(source);
        assertThat(link.getValue().getReview()).isSameAs(review);
        assertThat(link.getValue().isPrimarySource()).isFalse();
    }

    @Test
    void validatesTezaursEntriesAndSearchesByTheReviewTerm() {
        ReviewSuggestion review = inProgressReview();
        review.correctTerm("corrected");
        review.recordTezaursCheck(TezaursStatus.FOUND, 42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        service.findTezaursCandidates(review.getId(), null);
        verify(tezaursEntrySearchService).findMatches("corrected", 42);

        service.findTezaursCandidates(review.getId(), "  another term  ");
        verify(tezaursEntrySearchService).findMatches("another term", null);
        verifyNoInteractions(sourceSuggestionRepository);

        when(tezaursEntryRepository.existsById(99)).thenReturn(false);
        assertThatThrownBy(() -> service.recordTezaursCheck(review.getId(),
                new RecordTezaursCheckRequest(TezaursStatus.FOUND, 99)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void usesReviewIdsForCorpusEvidence() {
        ReviewSuggestion review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var response = service.addCorpusExample(review.getId(),
                new AddCorpusExampleRequest("https://korpuss.lv/id/42"));

        assertThat(response.reviewId()).isEqualTo(review.getId());
    }

    private static IncubatorSuggestion source() {
        return new IncubatorSuggestion(42, "term", "definition");
    }

    private static ReviewSuggestion inProgressReview() {
        ReviewSuggestion review = new ReviewSuggestion("term");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        return review;
    }
}
