package lv.tezaurs.suggestions.suggestion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import lv.tezaurs.suggestions.common.error.ConflictException;
import org.junit.jupiter.api.Test;

class SuggestionTest {

    @Test
    void enforcesTheIncubatorStatusGraph() {
        Map<SuggestionStatus, Set<SuggestionStatus>> expected = Map.of(
                SuggestionStatus.NEW, Set.of(SuggestionStatus.READY_FOR_REVIEW,
                        SuggestionStatus.IN_PROGRESS, SuggestionStatus.GARBAGE),
                SuggestionStatus.READY_FOR_REVIEW, Set.of(SuggestionStatus.IN_PROGRESS,
                        SuggestionStatus.COMPLETED, SuggestionStatus.NEW),
                SuggestionStatus.GARBAGE, Set.of(SuggestionStatus.NEW),
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
    void rejectsAStatusJumpThatIsNotInTheWorkflow() {
        IncubatorSuggestion source = source(SuggestionStatus.READY_FOR_REVIEW);

        assertThatThrownBy(() -> source.changeStatus(SuggestionStatus.GARBAGE))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Suggestion status cannot change from READY_FOR_REVIEW to GARBAGE");
    }

    @Test
    void identifiesStatusesThatNeedReviewData() {
        assertThat(SuggestionStatus.NEW.requiresReviewRecord()).isFalse();
        assertThat(SuggestionStatus.GARBAGE.requiresReviewRecord()).isFalse();
        assertThat(SuggestionStatus.READY_FOR_REVIEW.requiresReviewRecord()).isTrue();
        assertThat(SuggestionStatus.COMPLETED.requiresReviewRecord()).isTrue();
    }

    @Test
    void mapsAllLegacyDatabaseValues() {
        for (SuggestionStatus status : SuggestionStatus.values()) {
            assertThat(SuggestionStatus.fromDatabaseValue(status.databaseValue())).isEqualTo(status);
        }
        assertThat(SuggestionStatus.fromDatabaseValue(83)).isEqualTo(SuggestionStatus.NEEDS_EXPERT);
        assertThatThrownBy(() -> SuggestionStatus.fromDatabaseValue(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsReviewDataWithoutChangingTheSourceStatus() {
        Suggestion review = review();

        review.correctTerm("corrected");
        review.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42L);
        review.recordCorpusExample();

        assertThat(review.getReviewedTerm()).isEqualTo("corrected");
        assertThat(review.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_NOT_FOUND);
        assertThat(review.getMatchedEntryId()).isEqualTo(42L);
        assertThat(review.getCorpusStatus()).isEqualTo(CheckStatus.FOUND);
        assertThat(review.canComplete()).isTrue();
    }

    @Test
    void rejectsCorpusEvidenceBeforeAnAbsentMeaningIsConfirmed() {
        Suggestion review = review();

        assertThatThrownBy(review::recordCorpusExample)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Corpus evidence is only applicable when the submitted meaning is absent");
    }

    @Test
    void allowsAnEntryIdOnlyForAFoundDictionaryEntry() {
        Suggestion review = review();

        review.recordTezaursCheck(TezaursStatus.MEANING_FOUND, null);

        assertThat(review.getMatchedEntryId()).isNull();
        assertThatThrownBy(() -> review.recordTezaursCheck(TezaursStatus.NOT_CHECKED, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An entry ID is only allowed when a Tēzaurs entry was found");
    }

    private static IncubatorSuggestion source(SuggestionStatus status) {
        return new IncubatorSuggestion(42, "submitted", "definition", status);
    }

    private static Suggestion review() {
        return new Suggestion(42);
    }
}
