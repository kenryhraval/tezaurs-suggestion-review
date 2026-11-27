package lv.tezaurs.tip.corpus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.CorpusUnavailableException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.corpus.dto.CorpusSearchResponse;
import lv.tezaurs.tip.example.dto.ExampleResponse;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.dto.SelectCorpusExamplesRequest;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;
import lv.tezaurs.tip.example.service.ExampleService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class CorpusServiceTest {
    private static ValidatorFactory validatorFactory;
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExampleService examples = mock(ExampleService.class);
    private CorpusService service;
    private Review review;

    @BeforeAll
    static void startValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUpCorpusSelection() {
        service = spy(new CorpusService(reviews, examples, validatorFactory.getValidator()));
        review = new Review("vārds", "editor", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("editor");
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        when(examples.findSelectedTokenNumbers(review.getId(), Corpus.LVK2022)).thenAnswer(call ->
                new java.util.HashSet<>(examples.getExamplesForReview(review.getId()).stream()
                        .map(ExampleResponse::tokenNumber).toList()));
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        for (int page = 1; page <= 3; page++) {
            doReturn(createCorpusPage(page)).when(service).fetchCorpusPage(createCorpusSelectionRequest(null), page);
        }
        doAnswer(call -> {
            int sampleSize = call.getArgument(1);
            int pageNumber = call.getArgument(2);
            int total = Math.min(sampleSize, 5);
            List<CorpusSearchResponse.Example> sampledExamples = LongStream.rangeClosed(1, total)
                    .mapToObj(this::createCorpusExample).toList();
            return new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", pageNumber, sampleSize,
                    total, "https://korpuss.lv/search", sampledExamples);
        }).when(service).sampleExamples(any(), anyInt(), anyInt());
    }

    private CorpusSearchResponse.Example createCorpusExample(long token) {
        return new CorpusSearchResponse.Example(token, "", "vārds", "", "Piemērs " + token,
                "Avots", "Avots", null);
    }

    private CorpusSearchResponse createCorpusPage(int page) {
        long firstToken = (page - 1) * 2L + 1;
        var examples = LongStream.rangeClosed(firstToken, Math.min(firstToken + 1, 5))
                .mapToObj(token -> new CorpusSearchResponse.Example(token, "", "vārds", "", "Piemērs " + token,
                        "Avots", "Avots", null)).toList();
        return new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", page, 2, 5,
                "https://korpuss.lv/search", examples);
    }

    private ExampleResponse createSavedCorpusExample(long token) {
        return ExampleResponse.from(new Example(review.getId(), new SaveExampleRequest(ExampleSource.CORPUS,
                Corpus.LVK2022, token, "vārds", "Piemērs", "https://korpuss.lv", null, null, null, null), "editor"));
    }

    private SelectCorpusExamplesRequest createCorpusSelectionRequest(Integer count) {
        return new SelectCorpusExamplesRequest(Corpus.LVK2022, "vārds", true, count);
    }

    @SuppressWarnings("unchecked")
    private List<SaveExampleRequest> captureSavedCorpusSelection() {
        ArgumentCaptor<List<SaveExampleRequest>> selected = ArgumentCaptor.forClass(List.class);
        verify(examples).saveExamples(eq(review.getId()), selected.capture(), eq("auth"));
        return selected.getValue();
    }

    @Test
    void searchesWithoutLoadingOrCheckingAnyReview() {
        assertThat(service.fetchCorpusPage(createCorpusSelectionRequest(null), 2).page()).isEqualTo(2);
        verifyNoInteractions(reviews);
    }

    @Test
    void fetchesAllPagesBeforeSavingAndSkipsSavedOccurrences() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(createSavedCorpusExample(1)));
        service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(null), "auth");
        assertThat(captureSavedCorpusSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactly(2L, 3L, 4L, 5L);
        var order = inOrder(service, examples);
        order.verify(service).fetchCorpusPage(createCorpusSelectionRequest(null), 3);
        order.verify(examples).saveExamples(eq(review.getId()), anyList(), eq("auth"));
    }

    @Test
    void randomSelectionAllowsForSavedExamplesAndFetchesOneServerSample() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(createSavedCorpusExample(1), createSavedCorpusExample(2), createSavedCorpusExample(3)));
        service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(2), "auth");
        assertThat(captureSavedCorpusSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactlyInAnyOrder(4L, 5L);
        verify(service).sampleExamples(createCorpusSelectionRequest(2), 5, 1);
        verify(service, never()).searchExamples(anyString(), any(), anyInt(), anyBoolean());
    }

    @Test
    void selectingFiftyExamplesUsesOneSampleRequestInsteadOfFetchingRandomPages() {
        List<CorpusSearchResponse.Example> sampledExamples = LongStream.rangeClosed(1, 50)
                .mapToObj(this::createCorpusExample).toList();
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 1, 50, 50,
                "https://korpuss.lv/search", sampledExamples))
                .when(service).sampleExamples(createCorpusSelectionRequest(50), 50, 1);

        service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(50), "auth");

        assertThat(captureSavedCorpusSelection()).hasSize(50).extracting(SaveExampleRequest::tokenNumber).doesNotHaveDuplicates();
        verify(service, times(1)).sampleExamples(any(), anyInt(), anyInt());
        verify(service, never()).searchExamples(anyString(), any(), anyInt(), anyBoolean());
    }

    @Test
    void limitsTheSelectionWhenSavedExamplesDoNotMatchTheCurrentQuery() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(createSavedCorpusExample(100)));

        service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(2), "auth");

        assertThat(captureSavedCorpusSelection()).hasSize(2).extracting(SaveExampleRequest::tokenNumber)
                .doesNotContain(100L).doesNotHaveDuplicates();
        verify(service).sampleExamples(createCorpusSelectionRequest(2), 3, 1);
    }

    @Test
    void fetchesEverySampleBatchBeforeSavingAndSkipsDuplicateOccurrences() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(createSavedCorpusExample(1)));
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 4,
                "https://korpuss.lv/search", List.of(createCorpusExample(1), createCorpusExample(4))))
                .when(service).sampleExamples(createCorpusSelectionRequest(3), 4, 1);
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 2, 2, 4,
                "https://korpuss.lv/search", List.of(createCorpusExample(5), createCorpusExample(6))))
                .when(service).sampleExamples(createCorpusSelectionRequest(3), 4, 2);

        service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(3), "auth");

        assertThat(captureSavedCorpusSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactlyInAnyOrder(4L, 5L, 6L);
        var operations = inOrder(service, examples);
        operations.verify(service).sampleExamples(createCorpusSelectionRequest(3), 4, 2);
        operations.verify(examples).saveExamples(eq(review.getId()), anyList(), eq("auth"));
    }

    @Test
    void doesNotSavePartialSelectionsWhenASampleBatchFails() {
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 3,
                "https://korpuss.lv/search", List.of(createCorpusExample(1), createCorpusExample(2))))
                .when(service).sampleExamples(createCorpusSelectionRequest(3), 3, 1);
        doThrow(new CorpusUnavailableException(new RuntimeException()))
                .when(service).sampleExamples(createCorpusSelectionRequest(3), 3, 2);

        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(3), "auth"))
                .isInstanceOf(CorpusUnavailableException.class);
        verify(examples, never()).saveExamples(any(), any(), any());
    }

    @Test
    void returnsAllRemainingOccurrencesIfFewerThanRequestedAreAvailable() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(createSavedCorpusExample(1), createSavedCorpusExample(2), createSavedCorpusExample(3), createSavedCorpusExample(4)));
        service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(50), "auth");
        assertThat(captureSavedCorpusSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactly(5L);
    }

    @Test
    void doesNotSavePartialSelectionsWhenACorpusPageFails() {
        doThrow(new CorpusUnavailableException(new RuntimeException())).when(service)
                .fetchCorpusPage(createCorpusSelectionRequest(null), 2);
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(null), "auth"))
                .isInstanceOf(CorpusUnavailableException.class);
        verify(examples, never()).saveExamples(any(), any(), any());
    }

    @Test
    void rejectsChangedResultsAndInvalidCitationsWithoutSaving() {
        var emptyPage = new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 2, 2, 5,
                "https://korpuss.lv", List.of());
        doReturn(emptyPage).when(service).fetchCorpusPage(createCorpusSelectionRequest(null), 2);
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(null), "auth"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("mainījušies");
        var invalidExample = new CorpusSearchResponse.Example(1, "", "", "", "", "", "Avots", null);
        doReturn(new CorpusSearchResponse(
                Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 1, "https://korpuss.lv", List.of(invalidExample))).when(service).fetchCorpusPage(createCorpusSelectionRequest(null), 1);
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(null), "auth"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("nepilnīgus");
        verify(examples, never()).saveExamples(any(), any(), any());
    }

    @Test
    void handlesEmptySearchesWithoutSavingExamples() {
        doReturn(new CorpusSearchResponse(
                Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 0, "https://korpuss.lv", List.of())).when(service).fetchCorpusPage(createCorpusSelectionRequest(null), 1);
        service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(null), "auth");
        assertThat(captureSavedCorpusSelection()).isEmpty();
    }

    @Test
    void rejectsClosedAndMissingReviewsBeforeCallingTheCorpus() {
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(UUID.randomUUID(), createCorpusSelectionRequest(1), "auth"))
                .isInstanceOf(NotFoundException.class);
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        review.setLastStatusChangedBy("editor");
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), createCorpusSelectionRequest(1), "auth"))
                .isInstanceOf(ConflictException.class);
        verify(service, never()).searchExamples(anyString(), any(), anyInt(), anyBoolean());
    }
}
