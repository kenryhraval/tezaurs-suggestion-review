package lv.tezaurs.suggestions.suggestion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewCaseTest {
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
                ReviewStatus.ALREADY_EXISTS, Set.of(),
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
    void startsNewAndOwnsItsWorkflowState() {
        ReviewCase review = new ReviewCase("submitted", "reviewer");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.NEW);
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }

    @Test
    void recordsReviewData() {
        ReviewCase review = inProgressReview();
        review.correctTerm("corrected");
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42, "reviewer");

        assertThat(review.getReviewedTerm()).isEqualTo("corrected");
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_NOT_FOUND);
    }

    @Test
    void validatesStatusAndEvidenceTransitions() {
        ReviewCase review = new ReviewCase("submitted", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");

        ReviewCase inProgress = inProgressReview();
        assertThatThrownBy(() -> inProgress.changeStatus(ReviewStatus.ALREADY_EXISTS, "reviewer"))
                .isInstanceOf(ConflictException.class);
        inProgress.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42, "reviewer");
        assertThat(inProgress.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
    }

    @ParameterizedTest
    @EnumSource(value = TezaursStatus.class, names = {"FOUND", "MEANING_NOT_FOUND", "MEANING_NEEDS_EDITING"})
    void closingAsAlreadyExistingAlsoRecordsTheMeaningDecision(TezaursStatus previousDecision) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(previousDecision, 42, "reviewer");

        review.changeStatus(ReviewStatus.ALREADY_EXISTS, "reviewer");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_ALREADY_EXISTS);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
    }

    @Test
    void closingAsAlreadyExistingStillRequiresAMatchingEntry() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        review.recordUsageDecision(lv.tezaurs.suggestions.usage.entity.UsageDecision.USED, null, "reviewer");

        assertThatThrownBy(() -> review.changeStatus(ReviewStatus.ALREADY_EXISTS, "reviewer"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Select the matching Tēzaurs entry");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
    }

    @Test
    void rejectsReviewDataThatDoesNotMatchTheTezaursCheck() {
        ReviewCase review = inProgressReview();

        assertThatThrownBy(() -> review.recordTezaursCheck(TezaursStatus.NOT_FOUND, 42, "reviewer"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> review.recordTezaursCheck(TezaursStatus.FOUND, null, "reviewer"))
                .isInstanceOf(ConflictException.class);

    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"IN_PROGRESS", "ALREADY_EXISTS"}, mode = EnumSource.Mode.EXCLUDE)
    void requiresResumingBeforeChangingTheTezaursCheck(ReviewStatus status) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        if (status == ReviewStatus.NEW) review = new ReviewCase("submitted", "reviewer");
        else review.changeStatus(status, "reviewer");
        ReviewCase paused = review;
        TezaursStatus previousCheck = paused.getTezaursStatus();

        assertThatThrownBy(() -> paused.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42, "reviewer"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Resume the case in Apstrādē");
        assertThat(paused.getStatus()).isEqualTo(status);
        assertThat(paused.getTezaursStatus()).isEqualTo(previousCheck);
        assertThat(paused.getMatchedEntryId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void requiresResumingBeforeChangingTheTitle(ReviewStatus status) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42, "reviewer");
        if (status == ReviewStatus.NEW) review = new ReviewCase("submitted", "reviewer");
        else review.changeStatus(status, "reviewer");
        ReviewCase paused = review;

        assertThatThrownBy(() -> paused.correctTerm("changed"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Resume the case in Apstrādē");
        assertThat(paused.getReviewedTerm()).isEqualTo("submitted");
    }

    private static ReviewCase inProgressReview() {
        ReviewCase review = new ReviewCase("submitted", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.changeStatus(ReviewStatus.IN_PROGRESS, "reviewer");
        return review;
    }

    @Test
    void choosingSenseEditingReopensAnAlreadyExistingCase() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42, "reviewer");
        review.recordTezaursCheck(TezaursStatus.MEANING_NEEDS_EDITING, 42, "reviewer");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {
            "READY_FOR_REVIEW", "GARBAGE", "COMPLETED", "INVENTED",
            "ALREADY_EXISTS", "INSUFFICIENT_DATA", "NEEDS_EXPERT"})
    void recordsWhoActuallyChangedTheStatus(ReviewStatus nextStatus) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.FOUND, 42, "researcher");

        review.changeStatus(nextStatus, "editor");

        assertThat(review.getStatus()).isEqualTo(nextStatus);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void recordsThePersonWhoCompletedTheCase() {
        ReviewCase review = new ReviewCase("submitted", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "sorter");
        review.takeForReview("owner");

        review.changeStatus(ReviewStatus.COMPLETED, "editor");

        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void recordsAuthorsWhenDictionaryChecksCloseOrReopenTheCase() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.FOUND, 42, "researcher");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");

        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42, "closer");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42, "visitor");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("closer");

        review.recordTezaursCheck(TezaursStatus.MEANING_NEEDS_EDITING, 42, "editor");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void recordsUsageStatusChangesButNotRepeatedDecisionsOrTitleEdits() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "researcher");
        review.recordUsageDecision(UsageDecision.USED, null, "visitor");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
        review.correctTerm("corrected");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");

        review.recordUsageDecision(UsageDecision.NOT_CONFIRMED, null, "researcher");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("researcher");
        review.recordUsageDecision(UsageDecision.INVENTED, null, "linguist");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("linguist");
        review.recordUsageDecision(UsageDecision.USED, null, "expert");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("expert");
    }

    @Test
    void recordsThePersonWhoReleasedTheCase() {
        ReviewCase review = new ReviewCase("submitted", "reviewer");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "sorter");
        review.takeForReview("owner");
        assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");

        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "owner");

        assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");

        assertThatThrownBy(() -> review.changeStatus(ReviewStatus.NEW, "visitor"))
                .isInstanceOf(ConflictException.class);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("owner");
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {
            "INSUFFICIENT_DATA", "INVENTED"})
    void takingAPausedCasePreservesTheUsageAnswer(ReviewStatus pausedStatus) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        UsageDecision decision = UsageDecision.USED;
        if (pausedStatus == ReviewStatus.INSUFFICIENT_DATA) decision = UsageDecision.NOT_CONFIRMED;
        if (pausedStatus == ReviewStatus.INVENTED) decision = UsageDecision.INVENTED;
        review.recordUsageDecision(decision, "Previous assessment", "reviewer");
        if (review.getStatus() != pausedStatus) review.changeStatus(pausedStatus, "reviewer");

        review.takeForReview("editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(decision);
        assertThat(review.getUsageNote()).isEqualTo("Previous assessment");
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isNotNull();
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
    }

    @Test
    void releasingAndTakingACasePreservesTheUsageAssessment() {
        ReviewCase review = inProgressReview();
        review.takeForReview("reviewer");
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        review.recordUsageDecision(UsageDecision.USED, "Confirmed usage", "reviewer");
        var decisionTime = review.getUsageDecidedAt();

        review.changeStatus(ReviewStatus.READY_FOR_REVIEW, "reviewer");
        review.takeForReview("editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageNote()).isEqualTo("Confirmed usage");
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
        assertThat(review.canEditEntryDraft()).isTrue();
    }

    @Test
    void returningFromAnExpertPreservesTheUsageAssessment() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        review.recordUsageDecision(UsageDecision.USED, "Confirmed usage", "reviewer");
        var decisionTime = review.getUsageDecidedAt();
        review.changeStatus(ReviewStatus.NEEDS_EXPERT, "reviewer");

        review.takeForReview("editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageNote()).isEqualTo("Confirmed usage");
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isEqualTo(decisionTime);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("editor");
        assertThat(review.canEditEntryDraft()).isTrue();
    }

    @Test
    void reopeningAnAlreadyExistingCaseResetsTheUsageAnswer() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        review.recordUsageDecision(UsageDecision.USED, null, "reviewer");
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42, "reviewer");

        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.NOT_CHECKED);
        assertThat(review.getUsageDecidedBy()).isNull();
        assertThat(review.getUsageDecidedAt()).isNull();
    }

    @Test
    void takingAnAlreadyActiveCaseKeepsTheUsageAnswer() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        review.recordUsageDecision(UsageDecision.USED, null, "reviewer");

        review.takeForReview("reviewer");
        review.takeForReview("reviewer");

        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageDecidedBy()).isEqualTo("reviewer");
        assertThat(review.getUsageDecidedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = UsageDecision.class, names = {"NOT_CONFIRMED", "INVENTED"})
    void confirmingUsageOnAPausedCaseKeepsTheNewAnswer(UsageDecision previousDecision) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null, "reviewer");
        review.recordUsageDecision(previousDecision, null, "reviewer");

        review.recordUsageDecision(UsageDecision.USED, null, "editor");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getUsageDecision()).isEqualTo(UsageDecision.USED);
        assertThat(review.getUsageDecidedBy()).isEqualTo("editor");
        assertThat(review.getUsageDecidedAt()).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void neverCreatesACaseOrChangesStatusWithoutAKnownAuthor(String username) {
        assertThatThrownBy(() -> new ReviewCase("submitted", username))
                .isInstanceOf(IllegalArgumentException.class);
        ReviewCase review = inProgressReview();

        assertThatThrownBy(() -> review.changeStatus(ReviewStatus.NEEDS_EXPERT, username))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getLastStatusChangedBy()).isEqualTo("reviewer");
    }
}
