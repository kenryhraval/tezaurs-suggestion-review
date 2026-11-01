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
        Map<SuggestionStatus, Set<SuggestionStatus>> expected = Map.of(
                SuggestionStatus.NEW, Set.of(SuggestionStatus.READY_FOR_REVIEW,
                        SuggestionStatus.IN_PROGRESS, SuggestionStatus.GARBAGE),
                SuggestionStatus.READY_FOR_REVIEW, Set.of(SuggestionStatus.IN_PROGRESS,
                        SuggestionStatus.COMPLETED, SuggestionStatus.GARBAGE),
                SuggestionStatus.GARBAGE, Set.of(SuggestionStatus.READY_FOR_REVIEW),
                SuggestionStatus.IN_PROGRESS, Set.of(SuggestionStatus.INVENTED,
                        SuggestionStatus.ALREADY_EXISTS, SuggestionStatus.INSUFFICIENT_DATA,
                        SuggestionStatus.NEEDS_EXPERT, SuggestionStatus.COMPLETED,
                        SuggestionStatus.READY_FOR_REVIEW, SuggestionStatus.GARBAGE),
                SuggestionStatus.INVENTED, Set.of(SuggestionStatus.IN_PROGRESS, SuggestionStatus.GARBAGE),
                SuggestionStatus.ALREADY_EXISTS, Set.of(),
                SuggestionStatus.INSUFFICIENT_DATA,
                        Set.of(SuggestionStatus.IN_PROGRESS, SuggestionStatus.GARBAGE),
                SuggestionStatus.NEEDS_EXPERT, Set.of(SuggestionStatus.IN_PROGRESS),
                SuggestionStatus.COMPLETED, Set.of());

        for (SuggestionStatus source : SuggestionStatus.values()) {
            for (SuggestionStatus target : SuggestionStatus.values()) {
                assertThat(source.canTransitionTo(target))
                        .as("transition %s -> %s", source, target)
                        .isEqualTo(expected.get(source).contains(target));
            }
        }
    }

    @Test
    void startsNewAndOwnsItsWorkflowState() {
        ReviewCase review = new ReviewCase("submitted");

        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.NEW);
        review.changeStatus(SuggestionStatus.READY_FOR_REVIEW);
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.IN_PROGRESS);
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
        review.changeStatus(SuggestionStatus.READY_FOR_REVIEW);

        ReviewCase inProgress = inProgressReview();
        assertThatThrownBy(() -> inProgress.changeStatus(SuggestionStatus.ALREADY_EXISTS))
                .isInstanceOf(ConflictException.class);
        inProgress.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        assertThat(inProgress.getStatus()).isEqualTo(SuggestionStatus.ALREADY_EXISTS);
    }

    @ParameterizedTest
    @EnumSource(value = TezaursStatus.class, names = {"FOUND", "MEANING_NOT_FOUND", "MEANING_NEEDS_EDITING"})
    void closingAsAlreadyExistingAlsoRecordsTheMeaningDecision(TezaursStatus previousDecision) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(previousDecision, 42);

        review.changeStatus(SuggestionStatus.ALREADY_EXISTS);

        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.ALREADY_EXISTS);
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_ALREADY_EXISTS);
        assertThat(review.getMatchedEntryId()).isEqualTo(42);
    }

    @Test
    void closingAsAlreadyExistingStillRequiresAMatchingEntry() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        review.recordUsageDecision(lv.tezaurs.suggestions.usage.entity.UsageDecision.USED, null, null);

        assertThatThrownBy(() -> review.changeStatus(SuggestionStatus.ALREADY_EXISTS))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Select the matching Tēzaurs entry");
        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.IN_PROGRESS);
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
    @EnumSource(value = SuggestionStatus.class, names = {"IN_PROGRESS", "ALREADY_EXISTS"}, mode = EnumSource.Mode.EXCLUDE)
    void requiresResumingBeforeChangingTheTezaursCheck(SuggestionStatus status) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        if (status == SuggestionStatus.NEW) review = new ReviewCase("submitted");
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
    @EnumSource(value = SuggestionStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void requiresResumingBeforeChangingTheTitle(SuggestionStatus status) {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        if (status == SuggestionStatus.NEW) review = new ReviewCase("submitted");
        else review.changeStatus(status);
        ReviewCase paused = review;

        assertThatThrownBy(() -> paused.correctTerm("changed"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Resume the case in Apstrādē");
        assertThat(paused.getReviewedTerm()).isEqualTo("submitted");
    }

    private static ReviewCase inProgressReview() {
        ReviewCase review = new ReviewCase("submitted");
        review.changeStatus(SuggestionStatus.READY_FOR_REVIEW);
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        return review;
    }

    @Test
    void choosingSenseEditingReopensAnAlreadyExistingCase() {
        ReviewCase review = inProgressReview();
        review.recordTezaursCheck(TezaursStatus.MEANING_ALREADY_EXISTS, 42);
        review.recordTezaursCheck(TezaursStatus.MEANING_NEEDS_EDITING, 42);
        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.IN_PROGRESS);
    }
}
