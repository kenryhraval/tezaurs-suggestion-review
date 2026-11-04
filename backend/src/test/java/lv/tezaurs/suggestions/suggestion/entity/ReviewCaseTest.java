package lv.tezaurs.suggestions.suggestion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import lv.tezaurs.suggestions.common.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        ReviewCase review = new ReviewCase("submitted");

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.NEW);
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        review.changeStatus(ReviewStatus.IN_PROGRESS);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }

    @Test
    void recordsReviewData() {
        ReviewCase review = inProgressReview();
        review.correctTerm("corrected");
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);

        assertThat(review.getReviewedTerm()).isEqualTo("corrected");
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_NOT_FOUND);
    }

    @Test
    void validatesStatusAndEvidenceTransitions() {
        ReviewCase review = new ReviewCase("submitted");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);

        ReviewCase inProgress = inProgressReview();
        assertThatThrownBy(() -> inProgress.changeStatus(ReviewStatus.ALREADY_EXISTS))
                .isInstanceOf(ConflictException.class);
        inProgress.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        assertThat(inProgress.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
    }

    @ParameterizedTest
    @EnumSource(value = TezaursStatus.class, names = {"FOUND", "MEANING_NOT_FOUND", "MEANING_NEEDS_EDITING"})
    void closingAsAlreadyExistingAlsoRecordsTheMeaningDecision(TezaursStatus previousDecision) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(previousDecision, 42);

        review.changeStatus(ReviewStatus.ALREADY_EXISTS);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ALREADY_EXISTS);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_ALREADY_EXISTS);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
    }

    @Test
    void closingAsAlreadyExistingStillRequiresAMatchingEntry() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        review.recordUsageDecision(lv.tezaurs.suggestions.usage.entity.UsageDecision.USED, null, null);

        assertThatThrownBy(() -> review.changeStatus(ReviewStatus.ALREADY_EXISTS))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Select the matching Tēzaurs entry");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
    }

    @Test
    void rejectsReviewDataThatDoesNotMatchTheTezaursCheck() {
        ReviewCase review = inProgressReview();

        assertThatThrownBy(() -> review.recordTezaursCheck(TezaursStatus.NOT_FOUND, 42))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> review.recordTezaursCheck(TezaursStatus.FOUND, null))
                .isInstanceOf(ConflictException.class);

    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"IN_PROGRESS", "ALREADY_EXISTS"}, mode = EnumSource.Mode.EXCLUDE)
    void requiresResumingBeforeChangingTheTezaursCheck(ReviewStatus status) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        if (status == ReviewStatus.NEW) review = new ReviewCase("submitted");
        else review.changeStatus(status);
        ReviewCase paused = review;
        TezaursStatus previousCheck = paused.getTezaursStatus();

        assertThatThrownBy(() -> paused.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42))
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
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        if (status == ReviewStatus.NEW) review = new ReviewCase("submitted");
        else review.changeStatus(status);
        ReviewCase paused = review;

        assertThatThrownBy(() -> paused.correctTerm("changed"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Resume the case in Apstrādē");
        assertThat(paused.getReviewedTerm()).isEqualTo("submitted");
    }

    private static ReviewCase inProgressReview() {
        ReviewCase review = new ReviewCase("submitted");
        review.changeStatus(ReviewStatus.READY_FOR_REVIEW);
        review.changeStatus(ReviewStatus.IN_PROGRESS);
        return review;
    }

    @Test
    void choosingSenseEditingReopensAnAlreadyExistingCase() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        review.recordTezaursCheck(TezaursStatus.MEANING_NEEDS_EDITING, 42);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.IN_PROGRESS);
    }
}
