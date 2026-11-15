package lv.tezaurs.suggestions.usage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.suggestions.auth.service.CurrentUserService;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.common.error.NotFoundException;
import lv.tezaurs.suggestions.entry.service.EntryDraftService;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import lv.tezaurs.suggestions.suggestion.repository.ReviewCaseRepository;
import lv.tezaurs.suggestions.suggestion.service.ReviewCompletionService;
import lv.tezaurs.suggestions.usage.dto.*;
import lv.tezaurs.suggestions.usage.entity.*;
import lv.tezaurs.suggestions.usage.repository.UsageEvidenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UsageServiceTest {
    private final ReviewCaseRepository cases = mock(ReviewCaseRepository.class);
    private final UsageEvidenceRepository evidence = mock(UsageEvidenceRepository.class);
    private final EntryDraftService drafts = mock(EntryDraftService.class);
    private final ReviewCompletionService completion = mock(ReviewCompletionService.class);
    private final CorpusSearchService corpus = mock(CorpusSearchService.class);
    private final UsageService service = new UsageService(cases, evidence, drafts, completion, new CurrentUserService(), corpus);
    private ReviewCase review;

    @BeforeEach
    void prepareCase() {
        review = new ReviewCase("vārds", "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        review.changeType(CaseType.ENTRY, null, false, "reviewer");
        when(cases.findById(review.getId())).thenReturn(Optional.of(review));
        when(cases.findLockedById(review.getId())).thenReturn(Optional.of(review));
        when(evidence.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void displaysSavedCorpusCitationsWithoutMarkupAndPreservesTheStoredText() {
        var request = new SaveEvidenceRequest(EvidenceSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "<p>Pirmais.</p><p>Otrais &amp; trešais.</p>",
                "https://korpuss.lv", null, null);
        UsageEvidence saved = new UsageEvidence(review.getId(), request, "reviewer");
        when(evidence.findByCaseIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(saved));

        assertThat(service.evidence(review.getId())).singleElement().satisfies(response ->
                assertThat(response.citation()).isEqualTo("Pirmais. Otrais & trešais."));
        assertThat(saved.getCitation()).isEqualTo(request.citation());
        assertThat(CorpusTextFormatter.format("2 < 5; &lt;vārds&gt;")).isEqualTo("2 < 5; <vārds>");
    }

    @Test
    void confirmsUsageFromTheReviewersKnowledgeAndPostponesWithoutCallingItInvented() {
        assertThat(review.canEditEntryDraft()).isFalse();
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.NOT_CONFIRMED, "Nav datu"), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INSUFFICIENT_DATA);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("user");
        verifyNoInteractions(drafts);
        review.takeForReview("user");
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.USED, " Pazīstu šo vārdu. "), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageNote()).isEqualTo("Pazīstu šo vārdu.");
        assertThat(review.getUsageDecidedBy()).isEqualTo("user");
        assertThat(review.getUsageDecidedAt()).isNotNull();
        assertThat(review.canEditEntryDraft()).isTrue();
        verify(drafts).create(review.getId());
        var decisionTime = review.getUsageDecidedAt();
        review.correctTerm("cits vārds");
        assertThat(review.getReviewedTerm()).isEqualTo("cits vārds");
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageNote()).isEqualTo("Pazīstu šo vārdu.");
        assertThat(review.getUsageDecidedBy()).isEqualTo("user");
        assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
        assertThat(review.getType()).isEqualTo(CaseType.ENTRY);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.canEditEntryDraft()).isTrue();
    }

    @Test
    void distinguishesPostponementFromAnExplicitInventedDecision() {
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.NOT_CONFIRMED, "Jāturpina izpēte"), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INSUFFICIENT_DATA);
        review.takeForReview("user");
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.INVENTED, "Valodnieka izvērtējums"), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INVENTED);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.INVENTED);
        review.takeForReview("user");
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.NOT_CONFIRMED, null), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.INSUFFICIENT_DATA);
        review.takeForReview("user");
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.INVENTED, null), "Basic dXNlcjpwYXNz");
        review.takeForReview("user");
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        verify(drafts).create(review.getId());
    }

    @ParameterizedTest
    @EnumSource(value = UsageDecision.class, names = "NOT_CHECKED", mode = EnumSource.Mode.EXCLUDE)
    void usageDecisionsRequireAUsername(UsageDecision decision) {
        assertThatThrownBy(() -> service.decide(review.getId(), new UsageDecisionRequest(decision, null), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("jāpiesakās");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.NOT_CHECKED);
        verifyNoInteractions(drafts);
    }

    @Test
    void savesListsAndDeletesEvidenceOnlyWithinItsOwnCase() {
        var request = new SaveEvidenceRequest(EvidenceSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Korpusa piemērs", "https://korpuss.lv/search", "ID=1", "piezīme");
        var saved = service.addEvidence(review.getId(), request, null);
        assertThat(saved.source()).isEqualTo(EvidenceSource.CORPUS);
        assertThat(saved.query()).isEqualTo("vārds");
        assertThat(saved.corpus()).isEqualTo(Corpus.LVK2022);
        assertThat(saved.tokenNumber()).isEqualTo(123L);
        assertThat(saved.createdAt()).isNotNull();
        var row = new UsageEvidence(review.getId(), request, "user");
        when(evidence.findByCaseIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(row));
        assertThat(service.evidence(review.getId())).singleElement().satisfies(item -> {
            assertThat(item.url()).isEqualTo(request.url());
            assertThat(item.reference()).isEqualTo("ID=1");
            assertThat(item.note()).isEqualTo("piezīme");
            assertThat(item.createdBy()).isEqualTo("user");
        });
        when(evidence.findByIdAndCaseId(row.getId(), review.getId())).thenReturn(Optional.of(row));
        service.deleteEvidence(review.getId(), row.getId());
        verify(evidence).delete(row);
        assertThatThrownBy(() -> service.deleteEvidence(review.getId(), UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUnsupportedDecisionsAndWritesToClosedOrExistingEntryCases() {
        assertThatThrownBy(() -> service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.NOT_CHECKED, null), "Basic dXNlcjpwYXNz"))
                .isInstanceOf(ConflictException.class);
        review.changeStatus(ReviewStatus.COMPLETED, "reviewer");
        assertThatThrownBy(() -> service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null), "Basic dXNlcjpwYXNz"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addEvidence(review.getId(), external("https://example.com"), null))
                .isInstanceOf(ConflictException.class);
        ReviewCase other = new ReviewCase("lemt", "reviewer");
        other.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        other.changeType(CaseType.SENSE, 100, false, "reviewer");
        when(cases.findById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.evidence(other.getId())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.evidence(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"INSUFFICIENT_DATA", "INVENTED"})
    void pausedCasesKeepEvidenceReadableButRejectChangesUntilResumed(ReviewStatus pausedStatus) {
        UsageDecision previousDecision = pausedStatus == ReviewStatus.INSUFFICIENT_DATA
                ? UsageDecision.NOT_CONFIRMED : UsageDecision.INVENTED;
        var request = external("https://example.com");
        UsageEvidence saved = new UsageEvidence(review.getId(), request, "reviewer");
        when(evidence.findByCaseIdOrderByCreatedAtAscIdAsc(review.getId())).thenReturn(List.of(saved));
        service.decide(review.getId(), new UsageDecisionRequest(previousDecision, "Saglabāts vērtējums"),
                "Basic dXNlcjpwYXNz");

        assertThat(service.evidence(review.getId())).hasSize(1);
        assertThatThrownBy(() -> service.addEvidence(review.getId(), request, null))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.deleteEvidence(review.getId(), saved.getId()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null),
                "Basic dXNlcjpwYXNz"))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
        assertThat(review.getUsageNote()).isEqualTo("Saglabāts vērtējums");
        verifyNoInteractions(drafts);
        verify(evidence, never()).save(any());
        verify(evidence, never()).delete(any());

        review.takeForReview("user");
        assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
        service.addEvidence(review.getId(), request, null);
        service.decide(review.getId(), new UsageDecisionRequest(UsageDecision.USED, null), "Basic dXNlcjpwYXNz");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        verify(drafts).create(review.getId());
    }

    @Test
    void rejectsUnsafeSourceUrlsAndInconsistentCorpusMetadata() {
        for (String url : new String[]{"javascript:alert(1)", "https://", "bad url", "file:///tmp/example"}) {
            assertThatThrownBy(() -> service.addEvidence(review.getId(), external(url), null)).isInstanceOf(ConflictException.class);
        }
        assertThat(service.addEvidence(review.getId(), external("https://example.com"), null).source()).isEqualTo(EvidenceSource.EXTERNAL);
        assertThatThrownBy(() -> service.addEvidence(review.getId(), new SaveEvidenceRequest(
                EvidenceSource.CORPUS, null, null, "vārds", "citāts", "https://example.com", null, null), null))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addEvidence(review.getId(), new SaveEvidenceRequest(
                EvidenceSource.EXTERNAL, Corpus.LVK2022, null, "vārds", "citāts", "https://example.com", null, null), null))
                .isInstanceOf(ConflictException.class);
        service.search(review.getId(), "vārds", Corpus.LVK2022, 1);
        verify(corpus).search("vārds", Corpus.LVK2022, 1);
    }

    @Test
    void reusesTheSameOccurrenceButKeepsIdenticalSentencesAtDifferentTokensSeparate() {
        var first = new SaveEvidenceRequest(EvidenceSource.CORPUS, Corpus.LVK2022, 123L,
                "vārds", "Tas pats teikums.", "https://example.com", null, null);
        var saved = service.addEvidence(review.getId(), first, null);
        var existing = new UsageEvidence(review.getId(), first, "user");
        when(evidence.findByCaseIdAndCorpusAndTokenNumber(review.getId(), Corpus.LVK2022, 123L))
                .thenReturn(Optional.of(existing));
        assertThat(service.addEvidence(review.getId(), first, null).id()).isEqualTo(existing.getId());
        verify(evidence, times(1)).save(any());

        var second = new SaveEvidenceRequest(EvidenceSource.CORPUS, Corpus.LVK2022, 456L,
                "vārds", "Tas pats teikums.", "https://example.com", null, null);
        var other = service.addEvidence(review.getId(), second, null);
        assertThat(other.tokenNumber()).isEqualTo(456L);
        assertThat(other.id()).isNotEqualTo(saved.id());
    }

    private static SaveEvidenceRequest external(String url) {
        return new SaveEvidenceRequest(EvidenceSource.EXTERNAL, null, null, "vārds", "Citāts", url, null, null);
    }
}
