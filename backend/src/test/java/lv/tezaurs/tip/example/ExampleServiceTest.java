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
import lv.tezaurs.tip.example.dto.UpdateExampleRequest;
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
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final ExampleService service = new ExampleService(reviews, exampleRepository, users, senseRepository);
    private Review review;

    @BeforeEach
    void prepareReview() {
        when(users.usernameFrom("authorization")).thenReturn(Optional.of("user"));
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
    void removesOnlyUnapprovedExamplesFromTheChosenCorpus() {
        var removable = new Example(review.getId(), new SaveExampleRequest(ExampleSource.CORPUS,
                Corpus.LVK2022, 1L, "vārds", "Piemērs", "https://korpuss.lv", null, null, null, null), "editor");
        var preserved = new Example(review.getId(), createExternalExampleRequest("https://example.com"), "editor");
        var approved = new Example(review.getId(), new SaveExampleRequest(ExampleSource.CORPUS,
                Corpus.LVK2022, 2L, "vārds", "Apstiprināts piemērs", "https://korpuss.lv", null, null, null, null), "editor");
        approved.reassignTo(new Sense(review.getId(), null));
        approved.setApproved(true);
        when(exampleRepository.findForReview(eq(review.getId()), any()))
                .thenReturn(List.of(removable, preserved, approved), List.of(preserved, approved));

        assertThat(service.deleteUnapprovedCorpusExamples(review.getId(), Corpus.LVK2022))
                .extracting(response -> response.id()).containsExactly(preserved.getId(), approved.getId());
        verify(exampleRepository).deleteAll(List.of(removable));
    }

    @Test
    void rejectsBulkRemovalFromClosedReviews() {
        review.setStatus(ReviewStatus.NEEDS_EXPERT);
        review.setLastStatusChangedBy("editor");
        assertThatThrownBy(() -> service.deleteUnapprovedCorpusExamples(review.getId(), Corpus.LVK2022))
                .isInstanceOf(ConflictException.class);
        verify(exampleRepository, never()).deleteAll(any());
    }

    @Test
    void batchSavingReusesExistingOccurrencesAndKeepsSourceMetadata() {
        var request = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Piemērs", "https://korpuss.lv/search", "Avots", null, null, null);
        Example existing = new Example(review.getId(), request, "editor");
        when(exampleRepository.findByReviewIdAndCorpusAndTokenNumber(review.getId(), Corpus.LVK2022, 123L))
                .thenReturn(Optional.of(existing));
        var responses = service.saveExamples(review.getId(), List.of(request, createExternalExampleRequest("https://example.com")),
                "authorization");

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
                "https://korpuss.lv", null, null, null, null);
        Example saved = new Example(review.getId(), request, "reviewer");
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(saved));

        assertThat(service.getExamplesForReview(review.getId())).singleElement().satisfies(response ->
                assertThat(response.citation()).isEqualTo("Pirmais. Otrais & trešais."));
        assertThat(saved.getCitation()).isEqualTo(request.citation());
        assertThat(CorpusTextFormatter.format("2 < 5; &lt;vārds&gt;")).isEqualTo("2 < 5; <vārds>");
    }

    @Test
    void preservesTheExactCorpusMatchWhenReloadingSavedExamples() {
        var request = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "lemt", "Viņi lemj šodien.", "https://korpuss.lv", null, null, 5, 9);
        Example saved = new Example(review.getId(), request, "reviewer");
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(saved));

        assertThat(service.getExamplesForReview(review.getId())).singleElement().satisfies(response -> {
            assertThat(response.citation().substring(response.matchStart(), response.matchEnd())).isEqualTo("lemj");
        });
    }

    @Test
    void rejectsHighlightBoundariesOutsideTheSavedCitation() {
        var request = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "lemt", "Viņi lemj.", "https://korpuss.lv", null, null, 5, 100);

        assertThatThrownBy(() -> service.saveExample(review.getId(), request, null))
                .isInstanceOf(ConflictException.class).hasMessage("Piemēra izcēluma robežas nav derīgas.");
        verify(exampleRepository, never()).save(any());
    }

    @Test
    void savesListsAndDeletesExamplesOnlyWithinItsOwnReview() {
        var request = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Korpusa piemērs", "https://korpuss.lv/search", "ID=1", "piezīme", null, null);
        var saved = service.saveExample(review.getId(), request, null);
        assertThat(saved.source()).isEqualTo(ExampleSource.CORPUS);
        assertThat(saved.query()).isEqualTo("vārds");
        assertThat(saved.corpus()).isEqualTo(Corpus.LVK2022);
        assertThat(saved.tokenNumber()).isEqualTo(123L);
        assertThat(saved.createdAt()).isNotNull();
        var row = new Example(review.getId(), request, "user");
        when(exampleRepository.findForReview(eq(review.getId()), any())).thenReturn(List.of(row));
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
            assertThatThrownBy(() -> service.saveExample(review.getId(), createExternalExampleRequest(url), null)).isInstanceOf(ConflictException.class);
        }
        assertThat(service.saveExample(review.getId(), createExternalExampleRequest("https://example.com"), null).source()).isEqualTo(ExampleSource.EXTERNAL);
        assertThatThrownBy(() -> service.saveExample(review.getId(), new SaveExampleRequest(
                ExampleSource.CORPUS, null, null, "vārds", "citāts", "https://example.com", null, null, null, null), null))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.saveExample(review.getId(), new SaveExampleRequest(
                ExampleSource.EXTERNAL, Corpus.LVK2022, null, "vārds", "citāts", "https://example.com", null, null, null, null), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reusesTheSameOccurrenceButKeepsIdenticalSentencesAtDifferentTokensSeparate() {
        var first = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Tas pats teikums.", "https://example.com", null, null, null, null);
        var saved = service.saveExample(review.getId(), first, null);
        var existing = new Example(review.getId(), first, "user");
        when(exampleRepository.findByReviewIdAndCorpusAndTokenNumber(review.getId(), Corpus.LVK2022, 123L))
                .thenReturn(Optional.of(existing));
        assertThat(service.saveExample(review.getId(), first, null).id()).isEqualTo(existing.getId());
        verify(exampleRepository, times(1)).save(any());

        var second = new SaveExampleRequest(ExampleSource.CORPUS, Corpus.LVK2022, 456L,
                "vārds", "Tas pats teikums.", "https://example.com", null, null, null, null);
        var other = service.saveExample(review.getId(), second, null);
        assertThat(other.tokenNumber()).isEqualTo(456L);
        assertThat(other.id()).isNotEqualTo(saved.id());
    }

    @Test
    void movesExamplesBetweenApprovedUnapprovedAndExistingSenses() {
        Example example = new Example(review.getId(), createExternalExampleRequest("https://example.com"), "editor");
        when(exampleRepository.findByIdAndReviewId(example.getId(), review.getId())).thenReturn(Optional.of(example));
        Sense approved = new Sense(review.getId(), null);
        approved.setApproved(true);
        Sense unapproved = new Sense(review.getId(), null);
        Sense existing = new Sense(UUID.randomUUID(), review.getId(), null, "Esoša nozīme", 7, 3);
        for (Sense sense : List.of(approved, unapproved, existing)) {
            when(senseRepository.findByIdAndReviewId(sense.getId(), review.getId())).thenReturn(Optional.of(sense));
        }

        service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(approved.getId(), true));
        assertThat(example.isApproved()).isTrue();
        service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(unapproved.getId(), false));
        assertThat(approved.getExamples()).isEmpty();
        assertThat(unapproved.getExamples()).containsExactly(example);
        assertThatThrownBy(() -> service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(existing.getId(), true)))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isSameAs(unapproved);
        service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(existing.getId(), false));
        assertThat(unapproved.getExamples()).isEmpty();
        assertThat(existing.getExamples()).containsExactly(example);
        service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(null, false));
        assertThat(existing.getExamples()).isEmpty();
        assertThat(example.getSense()).isNull();
    }

    @Test
    void exampleApprovalIsIndependentOfSenseApprovalAndProtectsItFromRemoval() {
        Example example = new Example(review.getId(), createExternalExampleRequest("https://example.com"), "editor");
        Sense sense = new Sense(review.getId(), null);
        when(exampleRepository.findByIdAndReviewId(example.getId(), review.getId())).thenReturn(Optional.of(example));
        when(senseRepository.findByIdAndReviewId(sense.getId(), review.getId())).thenReturn(Optional.of(sense));

        assertThatThrownBy(() -> service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(null, true)))
                .isInstanceOf(ConflictException.class);
        service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(sense.getId(), true));
        assertThat(example.isApproved()).isTrue();
        assertThat(sense.isApproved()).isFalse();
        assertThatThrownBy(() -> service.deleteExample(review.getId(), example.getId())).isInstanceOf(ConflictException.class);
        service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(sense.getId(), false));
        service.deleteExample(review.getId(), example.getId());
        verify(exampleRepository).delete(example);
    }

    @Test
    void rejectsTransfersOutsideTheCurrentReviewAndEntryChoice() {
        Example example = new Example(review.getId(), createExternalExampleRequest("https://example.com"), "editor");
        when(exampleRepository.findByIdAndReviewId(example.getId(), review.getId())).thenReturn(Optional.of(example));
        UUID foreignSenseId = UUID.randomUUID();
        assertThatThrownBy(() -> service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(foreignSenseId, false)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateExample(review.getId(), UUID.randomUUID(), new UpdateExampleRequest(null, false)))
                .isInstanceOf(NotFoundException.class);

        Sense anotherEntry = new Sense(review.getId(), 200);
        when(senseRepository.findByIdAndReviewId(anotherEntry.getId(), review.getId())).thenReturn(Optional.of(anotherEntry));
        assertThatThrownBy(() -> service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(anotherEntry.getId(), false)))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isNull();

        example.reassignTo(anotherEntry);
        assertThatThrownBy(() -> service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(null, false)))
                .isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isSameAs(anotherEntry);

        review.setStatus(ReviewStatus.COMPLETED);
        assertThatThrownBy(() -> service.updateExample(review.getId(), example.getId(), new UpdateExampleRequest(null, false)))
                .isInstanceOf(ConflictException.class);
    }

    private static SaveExampleRequest createExternalExampleRequest(String url) {
        return new SaveExampleRequest(ExampleSource.EXTERNAL, null, null, "vārds", "Citāts", url, null, null, null, null);
    }
}
