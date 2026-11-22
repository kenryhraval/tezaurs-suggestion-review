package lv.tezaurs.tip.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.common.error.NotFoundException;
import lv.tezaurs.tip.common.util.CorpusTextFormatter;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;
import lv.tezaurs.tip.example.repository.ExampleRepository;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExampleServiceTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExampleRepository exampleRepository = mock(ExampleRepository.class);
    private final SenseRepository senseRepository = mock(SenseRepository.class);
    private final ExampleService service = new ExampleService(reviews, exampleRepository, new CurrentUserService(), senseRepository);
    private Review review;

    @BeforeEach
    void prepareReview() {
        review = new Review("vārds", "reviewer", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setLastStatusChangedBy("reviewer");
        review.setType(ReviewType.ENTRY);
        review.setMatchedEntryId(null);
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(exampleRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void removesOnlyUnassignedExamplesFromTheChosenCorpus() {
        var removable = new Example(review.getId(), new SaveExampleRequest(ExampleSource.CORPUS,
                Corpus.LVK2022, 1L, "vārds", "Piemērs", "https://korpuss.lv", null, null), "editor");
        var preserved = new Example(review.getId(), external("https://example.com"), "editor");
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(removable, preserved), List.of(preserved));

        assertThat(service.deleteCorpusExamplesOutsideDraft(review.getId(), Corpus.LVK2022))
                .singleElement().satisfies(response -> assertThat(response.id()).isEqualTo(preserved.getId()));
        verify(exampleRepository).deleteAll(List.of(removable));
    }

    @Test
    void rejectsBulkRemovalFromClosedReviews() {
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        review.setLastStatusChangedBy("editor");
        assertThatThrownBy(() -> service.deleteCorpusExamplesOutsideDraft(review.getId(), Corpus.LVK2022))
                .isInstanceOf(ConflictException.class);
        verify(exampleRepository, never()).deleteAll(any());
    }

    @Test
    void batchSavingReusesExistingOccurrencesAndKeepsSourceMetadata() {
        var request = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Piemērs", "https://korpuss.lv/search", "Avots", null);
        Example existing = new Example(review.getId(), request, "editor");
        when(exampleRepository.findByReviewIdAndCorpusAndTokenNumber(review.getId(), Corpus.LVK2022, 123L))
                .thenReturn(Optional.of(existing));
        var responses = service.saveExamples(review.getId(), List.of(request, external("https://example.com")),
                "Basic dXNlcjpwYXNz");

        assertThat(responses).hasSize(2);
        assertThat(responses.getFirst().id()).isEqualTo(existing.getId());
        assertThat(responses.getFirst().reference()).isEqualTo("Avots");
        assertThat(responses.getLast().createdBy()).isEqualTo("user");
        verify(exampleRepository, times(1)).save(any());
    }

    @Test
    void displaysSavedCorpusCitationsWithoutMarkupAndPreservesTheStoredText() {
        var request = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "<p>Pirmais.</p><p>Otrais &amp; trešais.</p>",
                "https://korpuss.lv", null, null);
        Example saved = new Example(review.getId(), request, "reviewer");
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(saved));

        assertThat(service.getExamplesForReview(review.getId())).singleElement().satisfies(response ->
                assertThat(response.citation()).isEqualTo("Pirmais. Otrais & trešais."));
        assertThat(saved.getCitation()).isEqualTo(request.citation());
        assertThat(CorpusTextFormatter.format("2 < 5; &lt;vārds&gt;")).isEqualTo("2 < 5; <vārds>");
    }

    @Test
    void savesListsAndDeletesEvidenceOnlyWithinItsOwnReview() {
        var request = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Korpusa piemērs", "https://korpuss.lv/search", "ID=1", "piezīme");
        var saved = service.saveExample(review.getId(), request, null);
        assertThat(saved.source()).isEqualTo(ExampleSource.CORPUS);
        assertThat(saved.query()).isEqualTo("vārds");
        assertThat(saved.corpus()).isEqualTo(Corpus.LVK2022);
        assertThat(saved.tokenNumber()).isEqualTo(123L);
        assertThat(saved.createdAt()).isNotNull();
        var row = new Example(review.getId(), request, "user");
        when(exampleRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(row));
        assertThat(service.getExamplesForReview(review.getId())).singleElement().satisfies(item -> {
            assertThat(item.url()).isEqualTo(request.url());
            assertThat(item.reference()).isEqualTo("ID=1");
            assertThat(item.note()).isEqualTo("piezīme");
            assertThat(item.createdBy()).isEqualTo("user");
        });
        when(exampleRepository.findByIdAndReviewId(row.getId(), review.getId())).thenReturn(Optional.of(row));
        service.deleteExample(review.getId(), row.getId());
        verify(exampleRepository).delete(row);
        assertThatThrownBy(() -> service.deleteExample(review.getId(), UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUnsafeSourceUrlsAndInconsistentCorpusMetadata() {
        for (String url : new String[]{"javascript:alert(1)", "https://", "bad url", "file:///tmp/example"}) {
            assertThatThrownBy(() -> service.saveExample(review.getId(), external(url), null)).isInstanceOf(ConflictException.class);
        }
        assertThat(service.saveExample(review.getId(), external("https://example.com"), null).source()).isEqualTo(ExampleSource.EXTERNAL);
        assertThatThrownBy(() -> service.saveExample(review.getId(), new SaveExampleRequest(
                ExampleSource.CORPUS, null, null, "vārds", "citāts", "https://example.com", null, null), null))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.saveExample(review.getId(), new SaveExampleRequest(
                ExampleSource.EXTERNAL, Corpus.LVK2022, null, "vārds", "citāts", "https://example.com", null, null), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reusesTheSameOccurrenceButKeepsIdenticalSentencesAtDifferentTokensSeparate() {
        var first = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Tas pats teikums.", "https://example.com", null, null);
        var saved = service.saveExample(review.getId(), first, null);
        var existing = new Example(review.getId(), first, "user");
        when(exampleRepository.findByReviewIdAndCorpusAndTokenNumber(review.getId(), Corpus.LVK2022, 123L))
                .thenReturn(Optional.of(existing));
        assertThat(service.saveExample(review.getId(), first, null).id()).isEqualTo(existing.getId());
        verify(exampleRepository, times(1)).save(any());

        var second = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 456L,
                "vārds", "Tas pats teikums.", "https://example.com", null, null);
        var other = service.saveExample(review.getId(), second, null);
        assertThat(other.tokenNumber()).isEqualTo(456L);
        assertThat(other.id()).isNotEqualTo(saved.id());
    }

    @Test
    void movesExamplesBetweenApprovedUnapprovedAndExistingSenses() {
        Example example = new Example(review.getId(), external("https://example.com"), "editor");
        when(exampleRepository.findByIdAndReviewId(example.getId(), review.getId())).thenReturn(Optional.of(example));
        Sense approved = new Sense(review.getId(), null);
        approved.setApproved(true);
        Sense unapproved = new Sense(review.getId(), null);
        Sense existing = new Sense(UUID.randomUUID(), review.getId(), null, "Esoša nozīme", 7, 3);
        for (Sense sense : List.of(approved, unapproved, existing)) {
            when(senseRepository.findByIdAndReviewId(sense.getId(), review.getId())).thenReturn(Optional.of(sense));
        }

        service.assignSense(review.getId(), example.getId(), approved.getId());
        assertThat(example.isAttachedToApprovedSense()).isTrue();
        service.assignSense(review.getId(), example.getId(), unapproved.getId());
        assertThat(approved.getExamples()).isEmpty();
        assertThat(unapproved.getExamples()).containsExactly(example);
        service.assignSense(review.getId(), example.getId(), existing.getId());
        assertThat(unapproved.getExamples()).isEmpty();
        assertThat(existing.getExamples()).containsExactly(example);
        service.assignSense(review.getId(), example.getId(), null);
        assertThat(existing.getExamples()).isEmpty();
        assertThat(example.getSense()).isNull();
    }

    @Test
    void rejectsTransfersOutsideTheCurrentReviewAndEntryChoice() {
        Example example = new Example(review.getId(), external("https://example.com"), "editor");
        when(exampleRepository.findByIdAndReviewId(example.getId(), review.getId())).thenReturn(Optional.of(example));
        UUID foreignSenseId = UUID.randomUUID();
        assertThatThrownBy(() -> service.assignSense(review.getId(), example.getId(), foreignSenseId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.assignSense(review.getId(), UUID.randomUUID(), null))
                .isInstanceOf(NotFoundException.class);

        Sense anotherEntry = new Sense(review.getId(), 200);
        when(senseRepository.findByIdAndReviewId(anotherEntry.getId(), review.getId())).thenReturn(Optional.of(anotherEntry));
        assertThatThrownBy(() -> service.assignSense(review.getId(), example.getId(), anotherEntry.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isNull();

        example.reassignTo(anotherEntry);
        assertThatThrownBy(() -> service.assignSense(review.getId(), example.getId(), null))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isSameAs(anotherEntry);

        review.setStatus(ReviewStatus.COMPLETED);
        assertThatThrownBy(() -> service.assignSense(review.getId(), example.getId(), null))
                .isInstanceOf(ConflictException.class);
    }

    private static SaveExampleRequest external(String url) {
        return new SaveExampleRequest(ExampleSource.EXTERNAL, null, null, "vārds", "Citāts", url, null, null);
    }
}
