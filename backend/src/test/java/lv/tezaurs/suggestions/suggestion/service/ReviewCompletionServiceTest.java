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
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
import lv.tezaurs.suggestions.suggestion.entity.TezaursStatus;
import org.junit.jupiter.api.Test;

class ReviewCompletionServiceTest {
    private final ReviewEntryRepository entries = mock(ReviewEntryRepository.class);
    private final ReviewLexemeRepository lexemes = mock(ReviewLexemeRepository.class);
    private final ReviewSenseRepository senses = mock(ReviewSenseRepository.class);
    private final ReviewCompletionService service = new ReviewCompletionService(entries, lexemes, senses);

    @Test
    void requiresTheTezaursAndMeaningDecisions() {
        ReviewCase review = review();
        assertBlocked(review, CompletionBlockReason.TEZAURS_CHECK_REQUIRED);
        review.recordTezaursCheck(TezaursStatus.FOUND, 42);
        assertBlocked(review, CompletionBlockReason.MEANING_CHECK_REQUIRED);
    }

    @Test
    void readsNewEntryAndPrimaryLexemesFromActualDrafts() {
        ReviewCase review = review();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        review.recordUsageDecision(lv.tezaurs.suggestions.usage.entity.UsageDecision.USED, null, null);
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
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        ReviewSense edited = new ReviewSense(review.getId(), 42);
        edited.recordSource(7);
        when(senses.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(edited));
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
        ReviewSense added = new ReviewSense(review.getId(), 42);
        when(senses.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(edited, added));
        assertReady(review);
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 99);
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        assertReady(review);
        when(senses.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of());
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
    }

    @Test
    void requiresSavedWorkOnlyWhenTheExistingMeaningNeedsEditing() {
        ReviewCase review = review();
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        assertReady(review);
        review.recordTezaursCheck(TezaursStatus.MEANING_NEEDS_EDITING, 42);
        assertBlocked(review, CompletionBlockReason.SENSE_DRAFT_REQUIRED);
        ReviewSense edit = new ReviewSense(review.getId(), 42);
        edit.recordSource(7);
        when(senses.findAllByCaseIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(edit));
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
        ReviewCase review = new ReviewCase("vārds");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        return review;
    }
}
