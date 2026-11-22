package lv.tezaurs.tip.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.entry.service.EntryService;
import lv.tezaurs.tip.review.dto.UpdateStatusRequest;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import org.junit.jupiter.api.Test;

class ReviewCompletionTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final EntryService entries = mock(EntryService.class);
    private final SenseRepository senses = mock(SenseRepository.class);
    private final ReviewService service = new ReviewService(reviews, mock(ReviewSuggestionRepository.class),
            mock(TezaursEntryRepository.class), new CurrentUserService(), entries, senses);

    @Test
    void requiresDictionaryCheckBeforeCompletion() {
        Review review = review(ReviewType.UNKNOWN, null);

        assertCompletionRejected(review, "Vispirms veiciet pārbaudi Tēzaurā.");
    }

    @Test
    void requiresConfirmedUsageForNewEntries() {
        Review review = review(ReviewType.ENTRY, null);

        assertCompletionRejected(review, "Vispirms apstipriniet vārda lietojumu.");
    }

    @Test
    void requiresAReadyEntryDraft() {
        Review review = review(ReviewType.ENTRY, null);
        review.setUsageDecision(UsageDecision.USED);

        assertCompletionRejected(review, "Saglabājiet šķirkļa melnrakstu ar vienu galveno leksēmu.");
    }

    @Test
    void requiresANewSenseForTheSelectedEntry() {
        Review review = review(ReviewType.SENSE, 99);
        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(approvedSense(review.getId(), 42)));

        assertCompletionRejected(review, "Pievienojiet melnrakstam vismaz vienu jaunu nozīmi.");
    }

    @Test
    void unapprovedCandidatesAndExistingSensesDoNotAllowCompletion() {
        Review review = review(ReviewType.SENSE, 42);
        Sense candidate = new Sense(UUID.randomUUID(), review.getId(), 42, "Priekšlikums", null, 1);
        Sense existing = new Sense(UUID.randomUUID(), review.getId(), 42, "Esoša nozīme", 7, 2);
        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(candidate, existing));

        assertCompletionRejected(review, "Pievienojiet melnrakstam vismaz vienu jaunu nozīmi.");
    }

    @Test
    void completesNewEntriesWithConfirmedUsageAndRequiredDrafts() {
        Review review = review(ReviewType.ENTRY, null);
        review.setUsageDecision(UsageDecision.USED);
        when(entries.isReady(review.getId())).thenReturn(true);
        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), null))
                .thenReturn(List.of(approvedSense(review.getId(), null)));

        assertThat(service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.COMPLETED), "Basic dXNlcjpwYXNz").status())
                .isEqualTo(ReviewStatus.COMPLETED);
    }

    @Test
    void completesNewSenseReviewsWithoutANewEntryDraftOrUsageDecision() {
        Review review = review(ReviewType.SENSE, 42);
        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(approvedSense(review.getId(), 42)));

        assertThat(service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.COMPLETED), "Basic dXNlcjpwYXNz").status())
                .isEqualTo(ReviewStatus.COMPLETED);
        verifyNoInteractions(entries);
    }

    @Test
    void readsAndOtherStatusChangesDoNotCheckDraftReadiness() {
        Review review = review(ReviewType.ENTRY, null);
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));

        assertThat(service.findById(review.getId()).id()).isEqualTo(review.getId());
        assertThat(service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.NEEDS_EXPERT), "Basic dXNlcjpwYXNz").status())
                .isEqualTo(ReviewStatus.NEEDS_EXPERT);
        verifyNoInteractions(entries, senses);
    }

    private void assertCompletionRejected(Review review, String message) {
        assertThatThrownBy(() -> service.changeStatus(review.getId(),
                new UpdateStatusRequest(ReviewStatus.COMPLETED), "Basic dXNlcjpwYXNz"))
                .isInstanceOf(ConflictException.class)
                .hasMessage(message);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
    }

    private Review review(ReviewType type, Integer entryId) {
        Review review = new Review("vārds", "reviewer", "case01");
        review.setStatus(ReviewStatus.IN_PROGRESS);
        review.setType(type);
        review.setMatchedEntryId(entryId);
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        return review;
    }
    private static Sense approvedSense(UUID reviewId, Integer entryId) {
        Sense sense = new Sense(reviewId, entryId);
        sense.setApproved(true);
        return sense;
    }

}
