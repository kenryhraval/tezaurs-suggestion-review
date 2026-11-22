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

class CorpusServiceSelectionTest {
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
    void prepare() {
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
            doReturn(page(page)).when(service).fetchCorpusPage(request(null), page);
        }
        doAnswer(call -> {
            int sampleSize = call.getArgument(1);
            int pageNumber = call.getArgument(2);
            int total = Math.min(sampleSize, 5);
            List<CorpusSearchResponse.Example> sampledExamples = LongStream.rangeClosed(1, total)
                    .mapToObj(this::corpusExample).toList();
            return new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", pageNumber, sampleSize,
                    total, "https://korpuss.lv/search", sampledExamples);
        }).when(service).sampleExamples(any(), anyInt(), anyInt());
    }

    private CorpusSearchResponse.Example corpusExample(long token) {
        return new CorpusSearchResponse.Example(token, "", "vārds", "", "Piemērs " + token,
                "Avots", "Avots", null);
    }

    private CorpusSearchResponse page(int page) {
        long firstToken = (page - 1) * 2L + 1;
        var examples = LongStream.rangeClosed(firstToken, Math.min(firstToken + 1, 5))
                .mapToObj(token -> new CorpusSearchResponse.Example(token, "", "vārds", "", "Piemērs " + token,
                        "Avots", "Avots", null)).toList();
        return new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", page, 2, 5,
                "https://korpuss.lv/search", examples);
    }

    private ExampleResponse saved(long token) {
        return ExampleResponse.from(new Example(review.getId(), new SaveExampleRequest(ExampleSource.CORPUS,
                Corpus.LVK2022, token, "vārds", "Piemērs", "https://korpuss.lv", null, null, null, null), "editor"));
    }

    private SelectCorpusExamplesRequest request(Integer count) {
        return new SelectCorpusExamplesRequest(Corpus.LVK2022, "vārds", true, count);
    }

    @SuppressWarnings("unchecked")
    private List<SaveExampleRequest> savedSelection() {
        ArgumentCaptor<List<SaveExampleRequest>> selected = ArgumentCaptor.forClass(List.class);
        verify(examples).saveExamples(eq(review.getId()), selected.capture(), eq("auth"));
        return selected.getValue();
    }

    @Test
    void searchesWithoutLoadingOrCheckingAnyReview() {
        assertThat(service.fetchCorpusPage(request(null), 2).page()).isEqualTo(2);
        verifyNoInteractions(reviews);
    }

    @Test
    void fetchesAllPagesBeforeSavingAndSkipsSavedOccurrences() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(saved(1)));
        service.selectAndSaveCorpusExamples(review.getId(), request(null), "auth");
        assertThat(savedSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactly(2L, 3L, 4L, 5L);
        var order = inOrder(service, examples);
        order.verify(service).fetchCorpusPage(request(null), 3);
        order.verify(examples).saveExamples(eq(review.getId()), anyList(), eq("auth"));
    }

    @Test
    void randomSelectionAllowsForSavedExamplesAndFetchesOneServerSample() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(saved(1), saved(2), saved(3)));
        service.selectAndSaveCorpusExamples(review.getId(), request(2), "auth");
        assertThat(savedSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactlyInAnyOrder(4L, 5L);
        verify(service).sampleExamples(request(2), 5, 1);
        verify(service, never()).searchExamples(anyString(), any(), anyInt(), anyBoolean());
    }

    @Test
    void selectingFiftyExamplesUsesOneSampleRequestInsteadOfFetchingRandomPages() {
        List<CorpusSearchResponse.Example> sampledExamples = LongStream.rangeClosed(1, 50)
                .mapToObj(this::corpusExample).toList();
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 1, 50, 50,
                "https://korpuss.lv/search", sampledExamples))
                .when(service).sampleExamples(request(50), 50, 1);

        service.selectAndSaveCorpusExamples(review.getId(), request(50), "auth");

        assertThat(savedSelection()).hasSize(50).extracting(SaveExampleRequest::tokenNumber).doesNotHaveDuplicates();
        verify(service, times(1)).sampleExamples(any(), anyInt(), anyInt());
        verify(service, never()).searchExamples(anyString(), any(), anyInt(), anyBoolean());
    }

    @Test
    void limitsTheSelectionWhenSavedExamplesDoNotMatchTheCurrentQuery() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(saved(100)));

        service.selectAndSaveCorpusExamples(review.getId(), request(2), "auth");

        assertThat(savedSelection()).hasSize(2).extracting(SaveExampleRequest::tokenNumber)
                .doesNotContain(100L).doesNotHaveDuplicates();
        verify(service).sampleExamples(request(2), 3, 1);
    }

    @Test
    void fetchesEverySampleBatchBeforeSavingAndSkipsDuplicateOccurrences() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(saved(1)));
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 4,
                "https://korpuss.lv/search", List.of(corpusExample(1), corpusExample(4))))
                .when(service).sampleExamples(request(3), 4, 1);
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 2, 2, 4,
                "https://korpuss.lv/search", List.of(corpusExample(5), corpusExample(6))))
                .when(service).sampleExamples(request(3), 4, 2);

        service.selectAndSaveCorpusExamples(review.getId(), request(3), "auth");

        assertThat(savedSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactlyInAnyOrder(4L, 5L, 6L);
        var operations = inOrder(service, examples);
        operations.verify(service).sampleExamples(request(3), 4, 2);
        operations.verify(examples).saveExamples(eq(review.getId()), anyList(), eq("auth"));
    }

    @Test
    void doesNotSavePartialSelectionsWhenASampleBatchFails() {
        doReturn(new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 3,
                "https://korpuss.lv/search", List.of(corpusExample(1), corpusExample(2))))
                .when(service).sampleExamples(request(3), 3, 1);
        doThrow(new CorpusUnavailableException(new RuntimeException()))
                .when(service).sampleExamples(request(3), 3, 2);

        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), request(3), "auth"))
                .isInstanceOf(CorpusUnavailableException.class);
        verify(examples, never()).saveExamples(any(), any(), any());
    }

    @Test
    void returnsAllRemainingOccurrencesIfFewerThanRequestedAreAvailable() {
        when(examples.getExamplesForReview(review.getId())).thenReturn(List.of(saved(1), saved(2), saved(3), saved(4)));
        service.selectAndSaveCorpusExamples(review.getId(), request(50), "auth");
        assertThat(savedSelection()).extracting(SaveExampleRequest::tokenNumber).containsExactly(5L);
    }

    @Test
    void doesNotSavePartialSelectionsWhenACorpusPageFails() {
        doThrow(new CorpusUnavailableException(new RuntimeException())).when(service)
                .fetchCorpusPage(request(null), 2);
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), request(null), "auth"))
                .isInstanceOf(CorpusUnavailableException.class);
        verify(examples, never()).saveExamples(any(), any(), any());
    }

    @Test
    void rejectsChangedResultsAndInvalidCitationsWithoutSaving() {
        var emptyPage = new CorpusSearchResponse(Corpus.LVK2022, "LVK2022", "vārds", 2, 2, 5,
                "https://korpuss.lv", List.of());
        doReturn(emptyPage).when(service).fetchCorpusPage(request(null), 2);
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), request(null), "auth"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("mainījušies");
        var invalidExample = new CorpusSearchResponse.Example(1, "", "", "", "", "", "Avots", null);
        doReturn(new CorpusSearchResponse(
                Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 1, "https://korpuss.lv", List.of(invalidExample))).when(service).fetchCorpusPage(request(null), 1);
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), request(null), "auth"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("nepilnīgus");
        verify(examples, never()).saveExamples(any(), any(), any());
    }

    @Test
    void handlesEmptySearchesWithoutSavingExamples() {
        doReturn(new CorpusSearchResponse(
                Corpus.LVK2022, "LVK2022", "vārds", 1, 2, 0, "https://korpuss.lv", List.of())).when(service).fetchCorpusPage(request(null), 1);
        service.selectAndSaveCorpusExamples(review.getId(), request(null), "auth");
        assertThat(savedSelection()).isEmpty();
    }

    @Test
    void rejectsClosedAndMissingReviewsBeforeCallingTheCorpus() {
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(UUID.randomUUID(), request(1), "auth"))
                .isInstanceOf(NotFoundException.class);
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        review.setLastStatusChangedBy("editor");
        assertThatThrownBy(() -> service.selectAndSaveCorpusExamples(review.getId(), request(1), "auth"))
                .isInstanceOf(ConflictException.class);
        verify(service, never()).searchExamples(anyString(), any(), anyInt(), anyBoolean());
    }
}
