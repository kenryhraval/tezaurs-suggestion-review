package lv.tezaurs.suggestions.suggestion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import lv.tezaurs.suggestions.common.error.ConflictException;
import org.junit.jupiter.api.Test;

class ReviewSuggestionTest {
    @Test
    void enforcesTheTezaursWorkflowGraph() {
        Map<SuggestionStatus, Set<SuggestionStatus>> expected = Map.of(
                SuggestionStatus.NEW, Set.of(SuggestionStatus.READY_FOR_REVIEW,
                        SuggestionStatus.IN_PROGRESS, SuggestionStatus.GARBAGE),
                SuggestionStatus.READY_FOR_REVIEW, Set.of(SuggestionStatus.IN_PROGRESS,
                        SuggestionStatus.COMPLETED),
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
        ReviewSuggestion review = new ReviewSuggestion(42, "submitted");

        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.NEW);
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        assertThat(review.getStatus()).isEqualTo(SuggestionStatus.IN_PROGRESS);
    }

    @Test
    void recordsReviewData() {
        ReviewSuggestion review = inProgressReview();
        review.correctTerm("corrected");
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42);
        review.recordCorpusExample();

        assertThat(review.getSourceSuggestionId()).isEqualTo(42);
        assertThat(review.getReviewedTerm()).isEqualTo("corrected");
        assertThat(review.getCorpusStatus()).isEqualTo(CheckStatus.FOUND);
    }

    @Test
    void validatesStatusAndEvidenceTransitions() {
        ReviewSuggestion review = new ReviewSuggestion(42, "submitted");
        review.changeStatus(SuggestionStatus.READY_FOR_REVIEW);
        assertThatThrownBy(() -> review.changeStatus(SuggestionStatus.GARBAGE))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(review::recordCorpusExample).isInstanceOf(ConflictException.class);

        ReviewSuggestion inProgress = inProgressReview();
        assertThatThrownBy(() -> inProgress.changeStatus(SuggestionStatus.ALREADY_EXISTS))
                .isInstanceOf(ConflictException.class);
        inProgress.recordTezaursCheck(TezaursStatus.MEANING_FOUND, 42);
        inProgress.changeStatus(SuggestionStatus.ALREADY_EXISTS);
        assertThat(inProgress.getStatus()).isEqualTo(SuggestionStatus.ALREADY_EXISTS);
    }

    private static ReviewSuggestion inProgressReview() {
        ReviewSuggestion review = new ReviewSuggestion(42, "submitted");
        review.changeStatus(SuggestionStatus.IN_PROGRESS);
        return review;
    }
}
