package lv.tezaurs.suggestions.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.entry.entity.ReviewEntry;
import lv.tezaurs.suggestions.entry.entity.ReviewLexeme;
import lv.tezaurs.suggestions.entry.repository.ReviewEntryRepository;
import lv.tezaurs.suggestions.entry.repository.ReviewLexemeRepository;
import lv.tezaurs.suggestions.sense.entity.ReviewSense;
import lv.tezaurs.suggestions.sense.repository.ReviewSenseRepository;
import lv.tezaurs.suggestions.suggestion.entity.CompletionBlockReason;
import lv.tezaurs.suggestions.suggestion.entity.ReviewCase;
import lv.tezaurs.suggestions.suggestion.entity.ReviewStatus;
import lv.tezaurs.suggestions.suggestion.entity.CaseType;
import org.junit.jupiter.api.Test;

class ReviewCompletionServiceTest {
    private final ReviewEntryRepository entries = mock(ReviewEntryRepository.class);
    private final ReviewLexemeRepository lexemes = mock(ReviewLexemeRepository.class);
    private final ReviewSenseRepository senses = mock(ReviewSenseRepository.class);
    private final ReviewCompletionService service = new ReviewCompletionService(entries, lexemes, senses);

    @Test
    void requiresCaseTypeSelection() {
        ReviewCase review = review();
        assertBlocked(review, CompletionBlockReason.TEZAURS_CHECK_REQUIRED);
    }

    @Test
    void readsNewEntryAndPrimaryLexemesFromActualDrafts() {
        ReviewCase review = review();
        review.changeType(CaseType.ENTRY, null, false, "reviewer");
        review.recordUsageDecision(lv.tezaurs.suggestions.usage.entity.UsageDecision.USED, null, "reviewer");
        assertBlocked(review, CompletionBlockReason.ENTRY_DRAFT_REQUIRED);
        ReviewEntry entry = new ReviewEntry(review.getId());
        when(entries.findByCaseId(review.getId())).thenReturn(Optional.of(entry));
        assertBlocked(review, CompletionBlockReason.ENTRY_DRAFT_REQUIRED);
        ReviewLexeme primary = new ReviewLexeme(entry.getId(), 1);
        primary.setPrimary(true);
        when(lexemes.findAllByEntryIdOrderByOrderNoAscIdAsc(entry.getId())).thenReturn(List.of(primary));
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
        when(senses.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), null))
                .thenReturn(List.of(new ReviewSense(review.getId())));
        assertReady(review);
        primary.setPrimary(false);
        assertBlocked(review, CompletionBlockReason.ENTRY_DRAFT_REQUIRED);
    }

    @Test
    void requiresANewSenseForAMissingMeaningAndUsesOnlyTheSelectedEntry() {
        ReviewCase review = review();
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
        ReviewSense added = new ReviewSense(review.getId(), 42);
        when(senses.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(added));
        assertReady(review);
        review.changeType(CaseType.SENSE, 99, false, "reviewer");
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
        review.changeType(CaseType.SENSE, 42, false, "reviewer");
        assertReady(review);
        when(senses.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of());
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
    }

    @Test
    void anAlreadyExistingMeaningNeedsNoDraft() {
        ReviewCase review = review();
        review.changeType(CaseType.SENSE, 42, true, "reviewer");
        assertReady(review);
    }

    private void assertBlocked(ReviewCase review, CompletionBlockReason reason) {
        assertThat(service.response(review).completionBlockReason()).isEqualTo(reason);
        assertThat(service.response(review).canComplete()).isFalse();
        assertThatThrownBy(() -> service.requireCompletionAllowed(review)).isInstanceOf(ConflictException.class);
    }

    private void assertReady(ReviewCase review) {
        assertThat(service.response(review).completionBlockReason()).isNull();
        assertThat(service.response(review).canComplete()).isTrue();
        service.requireCompletionAllowed(review);
    }

    private static ReviewCase review() {
        ReviewCase review = new ReviewCase("vārds", "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        return review;
    }
}
