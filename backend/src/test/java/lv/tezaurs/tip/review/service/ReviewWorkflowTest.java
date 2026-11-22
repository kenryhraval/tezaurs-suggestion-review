package lv.tezaurs.tip.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lv.tezaurs.tip.auth.service.CurrentUserService;
import lv.tezaurs.tip.review.dto.UpdateReviewTitleRequest;
import lv.tezaurs.tip.review.dto.UpdateReviewTypeRequest;
import lv.tezaurs.tip.review.dto.UpdateStatusRequest;
import lv.tezaurs.tip.review.dto.UsageDecisionRequest;
import lv.tezaurs.tip.review.entity.Review;
import lv.tezaurs.tip.review.entity.ReviewStatus;
import lv.tezaurs.tip.review.entity.ReviewType;
import lv.tezaurs.tip.review.entity.UsageDecision;
import lv.tezaurs.tip.review.repository.ReviewRepository;
import lv.tezaurs.tip.review.repository.ReviewSuggestionRepository;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.entry.service.EntryService;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.sense.repository.SenseRepository;
import lv.tezaurs.tip.tezaurs.repository.TezaursEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewWorkflowTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final TezaursEntryRepository tezaursEntries = mock(TezaursEntryRepository.class);
    private final EntryService entries = mock(EntryService.class);
    private final SenseRepository senses = mock(SenseRepository.class);
    private final ReviewService service = new ReviewService(reviews, mock(ReviewSuggestionRepository.class),
            tezaursEntries, new CurrentUserService(), entries, senses);

    private void registerReview(Review review) {
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviews.findLockedById(review.getId())).thenReturn(Optional.of(review));
    }

    private String authorization(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        byte[] credentials = (username + ":password").getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(credentials);
    }

    private void changeStatus(Review review, ReviewStatus status, String username) {
        registerReview(review);
        service.changeStatus(review.getId(), new UpdateStatusRequest(status), authorization(username));
    }

    private void changeType(Review review, ReviewType type, Integer entryId, boolean alreadyExists, String username) {
        registerReview(review);
        if (entryId != null) {
            when(tezaursEntries.existsById(entryId)).thenReturn(true);
        }
        service.changeType(review.getId(),
                new UpdateReviewTypeRequest(type, entryId, alreadyExists), authorization(username));
    }

    private void recordUsageDecision(Review review, UsageDecision decision, String note, String username) {
        registerReview(review);
        service.recordUsageDecision(review.getId(),
                new UsageDecisionRequest(decision, note), authorization(username));
    }

    private void updateTitle(Review review, String title) {
        registerReview(review);
        service.updateTitle(review.getId(), new UpdateReviewTitleRequest(title));
    }

    @Test
    void importedInProgressReviewsCanBeClaimedWithoutReplacingAnExistingReviewer() {
        Review review = new Review("vārds", ReviewStatus.IN_PROGRESS, "case01");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "mikus");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "laura");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("mikus");
    }

    @Test
    void enforcesTheTezaursWorkflowGraph() {
        Map<ReviewStatus, Set<ReviewStatus>> expected = Map.of(
                ReviewStatus.NEW, Set.of(ReviewStatus.READY_FOR_REVIEW,
                        ReviewStatus.IN_PROGRESS, ReviewStatus.GARBAGE),
                ReviewStatus.READY_FOR_REVIEW, Set.of(ReviewStatus.IN_PROGRESS,
                        ReviewStatus.COMPLETED, ReviewStatus.GARBAGE),
                ReviewStatus.GARBAGE, Set.of(ReviewStatus.READY_FOR_REVIEW),
                ReviewStatus.IN_PROGRESS, Set.of(ReviewStatus.INVENTED,
                        ReviewStatus.ALREADY_EXISTS, ReviewStatus.INSUFFICIENT_DATA,
                        ReviewStatus.NEEDS_EXPERT, ReviewStatus.COMPLETED,
                        ReviewStatus.READY_FOR_REVIEW, ReviewStatus.GARBAGE),
                ReviewStatus.INVENTED, Set.of(ReviewStatus.IN_PROGRESS, ReviewStatus.GARBAGE),
                ReviewStatus.ALREADY_EXISTS, Set.of(ReviewStatus.IN_PROGRESS),
                ReviewStatus.INSUFFICIENT_DATA,
                        Set.of(ReviewStatus.IN_PROGRESS, ReviewStatus.GARBAGE),
                ReviewStatus.NEEDS_EXPERT, Set.of(ReviewStatus.IN_PROGRESS),
                ReviewStatus.COMPLETED, Set.of());

        for (ReviewStatus source : ReviewStatus.values()) {
            for (ReviewStatus target : ReviewStatus.values()) {
                assertThat(source.canTransitionTo(target))
                        .as("transition %s -> %s", source, target)
                        .isEqualTo(expected.get(source).contains(target));
            }
        }
    }

    @Test
    void startsNewAndTransitionsThroughTheService() {
        Review review = new Review("submitted", "reviewer", "case01");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.NEW);
        changeStatus(review, ReviewStatus.READY_FOR_REVIEW, "reviewer");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }

    @Test
    void recordsReviewData() {
        Review review = inProgressReview();
        updateTitle(review, "corrected");
        assertThat(review.getPublicId()).isEqualTo("case01");
        changeType(review, ReviewType.SENSE, 42, false, "reviewer");

        assertThat(review.getReviewedTerm()).isEqualTo("corrected");
        assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
    }

    @Test
    void validatesStatusAndEvidenceTransitions() {
        Review review = new Review("submitted", "reviewer", "case01");
        changeStatus(review, ReviewStatus.READY_FOR_REVIEW, "reviewer");

        Review inProgress = inProgressReview();
        assertThatThrownBy(() -> changeStatus(inProgress, ReviewStatus.ALREADY_EXISTS, "reviewer"))
                .isInstanceOf(ConflictException.class);
        changeType(inProgress, ReviewType.SENSE, 42, true, "reviewer");
        assertThat(inProgress.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
    }

    @Test
    void closingASenseReviewAsAlreadyExistingKeepsItsTargetEntry() {
        Review review = inProgressReview();
        changeType(review, ReviewType.SENSE, 42, false, "reviewer");

        changeStatus(review, ReviewStatus.ALREADY_EXISTS, "reviewer");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
    }

    @Test
    void cannotCloseAsAlreadyExistingWhenTheSelectedEntryNoLongerExists() {
        Review review = inProgressReview();
        changeType(review, ReviewType.SENSE, 42, false, "reviewer");
        when(tezaursEntries.existsById(42)).thenReturn(false);

        assertThatThrownBy(() -> changeStatus(review, ReviewStatus.ALREADY_EXISTS, "closer"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Izvēlētais Tēzaura šķirklis netika atrasts.");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
        assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
    }

    @Test
    void closingAsAlreadyExistingStillRequiresAMatchingEntry() {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        recordUsageDecision(review, lv.tezaurs.tip.review.entity.UsageDecision.USED, null, "reviewer");

        assertThatThrownBy(() -> changeStatus(review, ReviewStatus.ALREADY_EXISTS, "reviewer"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Izvēlieties atbilstošo Tēzaura šķirkli.");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getType()).isEqualTo(ReviewType.ENTRY);
    }

    @Test
    void rejectsReviewDataThatDoesNotMatchTheTezaursCheck() {
        Review review = inProgressReview();

        assertThatThrownBy(() -> changeType(review, ReviewType.ENTRY, 42, false, "reviewer"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> changeType(review, ReviewType.SENSE, null, false, "reviewer"))
                .isInstanceOf(ConflictException.class);

    }

    @Test
    void rejectsContradictoryTypeChoicesWithoutChangingTheReview() {
        Review review = inProgressReview();

        assertThatThrownBy(() -> changeType(review, ReviewType.ENTRY, null, true, "reviewer"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> changeType(review, ReviewType.UNKNOWN, 42, false, "reviewer"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> changeType(review, null, null, false, "reviewer"))
                .isInstanceOf(ConflictException.class);

        assertThat(review.getType()).isEqualTo(ReviewType.UNKNOWN);
        assertThat(review.getMatchedEntryId()).isNull();
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }

    @Test
    void selectingAnExistingMeaningClosesTheReviewAndFurtherEditsRequireResuming() {
        Review review = inProgressReview();

        changeType(review, ReviewType.SENSE, 42, true, "closer");

        assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");

        assertThatThrownBy(() -> changeType(review, ReviewType.SENSE, 99, false, "editor"))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "editor");
        changeType(review, ReviewType.SENSE, 99, false, "editor");

        assertThat(review.getType()).isEqualTo(ReviewType.SENSE);
        assertThat(review.getMatchedEntryId()).isEqualTo(99);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void requiresResumingBeforeChangingTheTezaursCheck(ReviewStatus status) {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        if (status == ReviewStatus.NEW) review = new Review("submitted", "reviewer", "case01");
        else review.setStatus(status);
        Review paused = review;
        ReviewType previousCheck = paused.getType();

        assertThatThrownBy(() -> changeType(paused, ReviewType.SENSE, 42, false, "reviewer"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Apstrādē");
        assertThat(paused.getStatus()).isEqualTo(status);
        assertThat(paused.getType()).isEqualTo(previousCheck);
        assertThat(paused.getMatchedEntryId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void requiresResumingBeforeChangingTheTitle(ReviewStatus status) {
        Review review = inProgressReview();
        changeType(review, ReviewType.SENSE, 42, false, "reviewer");
        if (status == ReviewStatus.NEW) review = new Review("submitted", "reviewer", "case01");
        else review.setStatus(status);
        Review paused = review;

        assertThatThrownBy(() -> updateTitle(paused, "changed"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Apstrādē");
        assertThat(paused.getReviewedTerm()).isEqualTo("submitted");
    }

    private Review inProgressReview() {
        Review review = new Review("submitted", "reviewer", "case01");
        changeStatus(review, ReviewStatus.READY_FOR_REVIEW, "reviewer");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");
        return review;
    }

    @Test
    void resumingAnAlreadyExistingReviewAllowsCorrectingTheMeaningChoice() {
        Review review = inProgressReview();
        changeType(review, ReviewType.SENSE, 42, true, "reviewer");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");
        changeType(review, ReviewType.SENSE, 42, false, "reviewer");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {
            "READY_FOR_REVIEW", "GARBAGE", "COMPLETED", "INVENTED",
            "ALREADY_EXISTS", "INSUFFICIENT_DATA", "NEEDS_EXPERT"})
    void recordsWhoActuallyChangedTheStatus(ReviewStatus nextStatus) {
        Review review = inProgressReview();
        changeType(review, ReviewType.SENSE, 42, false, "researcher");

        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(approvedSense(review.getId(), 42)));
        changeStatus(review, nextStatus, "editor");

        assertThat(review.getStatus()).isEqualTo(nextStatus);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void recordsThePersonWhoCompletedTheReview() {
        Review review = new Review("submitted", "reviewer", "case01");
        changeStatus(review, ReviewStatus.READY_FOR_REVIEW, "sorter");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "owner");

        changeType(review, ReviewType.SENSE, 42, false, "owner");
        when(senses.findAllByReviewIdAndTargetEntryIdOrderByOrderNoAscIdAsc(review.getId(), 42))
                .thenReturn(List.of(approvedSense(review.getId(), 42)));
        changeStatus(review, ReviewStatus.COMPLETED, "editor");

        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void recordsAuthorsWhenClosingAndExplicitlyResumingTheReview() {
        Review review = inProgressReview();
        changeType(review, ReviewType.SENSE, 42, false, "researcher");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");

        changeType(review, ReviewType.SENSE, 42, true, "closer");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");
        assertThatThrownBy(() -> changeType(review, ReviewType.SENSE, 42, true, "visitor"))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "editor");
        changeType(review, ReviewType.SENSE, 42, false, "editor");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void recordsUsageStatusChangesButNotRepeatedDecisionsOrTitleEdits() {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "researcher");
        recordUsageDecision(review, UsageDecision.USED, null, "visitor");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
        updateTitle(review, "corrected");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");

        recordUsageDecision(review, UsageDecision.NOT_CONFIRMED, null, "researcher");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("researcher");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "linguist");
        recordUsageDecision(review, UsageDecision.INVENTED, null, "linguist");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("linguist");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "expert");
        recordUsageDecision(review, UsageDecision.USED, null, "expert");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("expert");
    }

    @Test
    void recordsThePersonWhoReleasedTheReview() {
        Review review = new Review("submitted", "reviewer", "case01");
        changeStatus(review, ReviewStatus.READY_FOR_REVIEW, "sorter");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "owner");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");

        changeStatus(review, ReviewStatus.READY_FOR_REVIEW, "owner");

        assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");

        assertThatThrownBy(() -> changeStatus(review, ReviewStatus.NEW, "visitor"))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {
            "INSUFFICIENT_DATA", "INVENTED"})
    void takingAPausedReviewPreservesTheUsageAnswer(ReviewStatus pausedStatus) {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        UsageDecision decision = UsageDecision.USED;
        if (pausedStatus == ReviewStatus.INSUFFICIENT_DATA) decision = UsageDecision.NOT_CONFIRMED;
        if (pausedStatus == ReviewStatus.INVENTED) decision = UsageDecision.INVENTED;
        recordUsageDecision(review, decision, "Previous assessment", "reviewer");
        if (review.getStatus() != pausedStatus) changeStatus(review, pausedStatus, "reviewer");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(decision);
        assertThat(review.getUsageNote()).isEqualTo("Previous assessment");
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isNotNull();
        assertThat(review.getType()).isEqualTo(ReviewType.ENTRY);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void releasingAndTakingAReviewPreservesTheUsageAssessment() {
        Review review = inProgressReview();
        changeStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        recordUsageDecision(review, UsageDecision.USED, "Confirmed usage", "reviewer");
        var decisionTime = review.getUsageDecidedAt();

        changeStatus(review, ReviewStatus.READY_FOR_REVIEW, "reviewer");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageNote()).isEqualTo("Confirmed usage");
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
        assertThat(review.getType()).isEqualTo(ReviewType.ENTRY);
    }

    @Test
    void returningFromAnExpertPreservesTheUsageAssessment() {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        recordUsageDecision(review, UsageDecision.USED, "Confirmed usage", "reviewer");
        var decisionTime = review.getUsageDecidedAt();
        changeStatus(review, ReviewStatus.NEEDS_EXPERT, "reviewer");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageNote()).isEqualTo("Confirmed usage");
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
        assertThat(review.getType()).isEqualTo(ReviewType.ENTRY);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void reopeningAnAlreadyExistingReviewPreservesTheUsageAssessment() {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        recordUsageDecision(review, UsageDecision.USED, "Confirmed usage", "reviewer");
        var decisionTime = review.getUsageDecidedAt();
        changeType(review, ReviewType.SENSE, 42, true, "reviewer");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "editor");
        changeType(review, ReviewType.ENTRY, null, false, "editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageNote()).isEqualTo("Confirmed usage");
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void takingAnAlreadyActiveReviewKeepsTheUsageAnswer() {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        recordUsageDecision(review, UsageDecision.USED, null, "reviewer");

        changeStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");
        changeStatus(review, ReviewStatus.IN_PROGRESS, "reviewer");

        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = UsageDecision.class, names = {"NOT_CONFIRMED", "INVENTED"})
    void usageDecisionCanChangeOnlyAfterResumingAPausedReview(UsageDecision previousDecision) {
        Review review = inProgressReview();
        changeType(review, ReviewType.ENTRY, null, false, "reviewer");
        recordUsageDecision(review, previousDecision, null, "reviewer");

        assertThatThrownBy(() -> recordUsageDecision(review, UsageDecision.USED, null, "editor"))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
        changeStatus(review, ReviewStatus.IN_PROGRESS, "editor");
        assertThat(review.getUsageDecision()).isEqualTo(previousDecision);
        recordUsageDecision(review, UsageDecision.USED, null, "editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageDecidedBy()).isEqualTo("editor");
        assertThat(review.getUsageDecidedAt()).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void neverCreatesAReviewOrChangesStatusWithoutAKnownAuthor(String username) {
        assertThatThrownBy(() -> new Review("submitted", username, "case01"))
                .isInstanceOf(IllegalArgumentException.class);
        Review review = inProgressReview();

        assertThatThrownBy(() -> changeStatus(review, ReviewStatus.NEEDS_EXPERT, username))
                .isInstanceOf(ConflictException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
    }
    private static Sense approvedSense(UUID reviewId, Integer entryId) {
        Sense sense = new Sense(reviewId, entryId);
        sense.setApproved(true);
        return sense;
    }

}
