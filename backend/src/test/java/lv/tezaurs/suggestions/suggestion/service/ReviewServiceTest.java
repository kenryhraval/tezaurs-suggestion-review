package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Optional;
import lv.tezaurs.suggestions.meaning.entity.Meaning;
import lv.tezaurs.suggestions.meaning.entity.MeaningOrigin;
import lv.tezaurs.suggestions.meaning.repository.MeaningRepository;
import lv.tezaurs.suggestions.suggestion.dto.AddCorpusExampleRequest;
import lv.tezaurs.suggestions.suggestion.entity.CorpusExample;
import lv.tezaurs.suggestions.suggestion.entity.IncubatorSuggestion;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import lv.tezaurs.suggestions.suggestion.repository.CorpusExampleRepository;
import lv.tezaurs.suggestions.suggestion.repository.IncubatorSuggestionRepository;
import lv.tezaurs.suggestions.suggestion.repository.SuggestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;

class ReviewServiceTest {
    private IncubatorSuggestionRepository incubatorRepository;
    private SuggestionRepository reviewRepository;
    private MeaningRepository meaningRepository;
    private CorpusExampleRepository corpusExampleRepository;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        incubatorRepository = mock(IncubatorSuggestionRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        reviewRepository = mock(SuggestionRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        meaningRepository = mock(MeaningRepository.class, withSettings().mockMaker(MockMakers.PROXY));
        corpusExampleRepository = mock(CorpusExampleRepository.class,
                withSettings().mockMaker(MockMakers.PROXY));
        service = new ReviewService(incubatorRepository, reviewRepository,
                meaningRepository, corpusExampleRepository);
        when(reviewRepository.saveAndFlush(any(Suggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(meaningRepository.save(any(Meaning.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(corpusExampleRepository.saveAndFlush(any(CorpusExample.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void returnsNoReviewForANewSourceSuggestion() {
        IncubatorSuggestion source = source(SuggestionStatus.NEW);
        when(incubatorRepository.findById(42)).thenReturn(Optional.of(source));
        when(incubatorRepository.findByIdForUpdate(42)).thenReturn(Optional.of(source));

        assertThat(service.findBySourceSuggestionId(42)).isEmpty();
        verify(reviewRepository, never()).saveAndFlush(any(Suggestion.class));
    }

    @Test
    void createsMissingReviewDataWhenAReviewableSuggestionIsOpened() {
        IncubatorSuggestion source = source(SuggestionStatus.READY_FOR_REVIEW);
        when(incubatorRepository.findById(42)).thenReturn(Optional.of(source));
        when(incubatorRepository.findByIdForUpdate(42)).thenReturn(Optional.of(source));

        var response = service.findBySourceSuggestionId(42).orElseThrow();

        assertThat(response.sourceSuggestionId()).isEqualTo(42);
        ArgumentCaptor<Meaning> meaning = ArgumentCaptor.forClass(Meaning.class);
        verify(meaningRepository).save(meaning.capture());
        assertThat(meaning.getValue().getReviewId()).isEqualTo(response.id());
        assertThat(meaning.getValue().getOrigin()).isEqualTo(MeaningOrigin.SUBMITTER);
    }

    @Test
    void usesReviewIdsForReviewDataAndCorpusEvidence() {
        Suggestion review = new Suggestion(42);
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        var response = service.addCorpusExample(review.getId(),
                new AddCorpusExampleRequest("https://korpuss.lv/id/42"));

        assertThat(response.reviewId()).isEqualTo(review.getId());
        ArgumentCaptor<CorpusExample> example = ArgumentCaptor.forClass(CorpusExample.class);
        verify(corpusExampleRepository).saveAndFlush(example.capture());
        assertThat(example.getValue().getReviewId()).isEqualTo(review.getId());
    }

    private static IncubatorSuggestion source(SuggestionStatus status) {
        return new IncubatorSuggestion(42, "term", "definition", status);
    }
}
