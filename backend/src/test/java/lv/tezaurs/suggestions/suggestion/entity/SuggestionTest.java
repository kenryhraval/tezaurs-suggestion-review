package lv.tezaurs.suggestions.suggestion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lv.tezaurs.suggestions.common.error.ConflictException;
import org.junit.jupiter.api.Test;

class SuggestionTest {

    @Test
    void changesStatusWithoutAComplexTransitionGraph() {
        Suggestion suggestion = suggestion();

        suggestion.changeStatus(SuggestionStatus.NEEDS_EXPERT);
        suggestion.changeStatus(SuggestionStatus.IN_PROGRESS);

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.IN_PROGRESS);
    }

    @Test
    void preservesTheSubmittedTermWhenItIsCorrected() {
        Suggestion suggestion = suggestion();

        suggestion.correctTerm("corrected");

        assertThat(suggestion.getSubmittedTerm()).isEqualTo("submitted");
        assertThat(suggestion.getReviewedTerm()).isEqualTo("corrected");
    }

    @Test
    void recordsDictionaryChecksAndCorpusEvidence() {
        Suggestion suggestion = suggestion();

        suggestion.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42L);
        suggestion.recordCorpusExample();

        assertThat(suggestion.getTezaursStatus()).isEqualTo(TezaursStatus.MEANING_NOT_FOUND);
        assertThat(suggestion.getMatchedEntryId()).isEqualTo(42L);
        assertThat(suggestion.getCorpusStatus()).isEqualTo(CheckStatus.FOUND);

        suggestion.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);

        assertThat(suggestion.getTezaursStatus()).isEqualTo(TezaursStatus.NOT_FOUND);
        assertThat(suggestion.getMatchedEntryId()).isNull();
        assertThat(suggestion.getCorpusStatus()).isEqualTo(CheckStatus.FOUND);
    }

    @Test
    void rejectsCorpusEvidenceBeforeAnAbsentMeaningIsConfirmed() {
        Suggestion suggestion = suggestion();

        assertThatThrownBy(suggestion::recordCorpusExample)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Corpus evidence is only applicable when the submitted meaning is absent");
    }

    @Test
    void allowsAnOptionalEntryIdOnlyForAFoundDictionaryEntry() {
        Suggestion suggestion = suggestion();

        suggestion.recordTezaursCheck(TezaursStatus.MEANING_FOUND, null);

        assertThat(suggestion.getMatchedEntryId()).isNull();
        assertThatThrownBy(() -> suggestion.recordTezaursCheck(TezaursStatus.NOT_CHECKED, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An entry ID is only allowed when a Tēzaurs entry was found");
    }

    @Test
    void completesAnExistingMeaningAndReopensItWhenTheCheckChanges() {
        Suggestion suggestion = suggestion();
        suggestion.changeStatus(SuggestionStatus.IN_PROGRESS);

        suggestion.recordTezaursCheck(TezaursStatus.MEANING_FOUND, 42L);

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.COMPLETED);

        suggestion.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42L);

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.IN_PROGRESS);
    }

    @Test
    void requiresAllApplicableChecksBeforeManualCompletion() {
        Suggestion suggestion = suggestion();
        suggestion.changeStatus(SuggestionStatus.IN_PROGRESS);

        assertThatThrownBy(() -> suggestion.changeStatus(SuggestionStatus.COMPLETED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("All required review steps must be completed first");
        assertThat(suggestion.canComplete()).isFalse();
        assertThat(suggestion.completionBlockReason())
                .isEqualTo(CompletionBlockReason.TEZAURS_CHECK_REQUIRED);

        suggestion.recordTezaursCheck(TezaursStatus.FOUND, 42L);
        assertThatThrownBy(() -> suggestion.changeStatus(SuggestionStatus.COMPLETED))
                .isInstanceOf(ConflictException.class);
        assertThat(suggestion.completionBlockReason())
                .isEqualTo(CompletionBlockReason.MEANING_CHECK_REQUIRED);

        suggestion.recordTezaursCheck(TezaursStatus.NOT_FOUND, null);
        suggestion.changeStatus(SuggestionStatus.COMPLETED);

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.COMPLETED);
        assertThat(suggestion.canComplete()).isTrue();
        assertThat(suggestion.completionBlockReason()).isNull();
    }

    @Test
    void defaultsTheCorpusResultWhenAnExistingEntryLacksTheMeaning() {
        Suggestion suggestion = suggestion();
        suggestion.changeStatus(SuggestionStatus.IN_PROGRESS);
        suggestion.recordTezaursCheck(TezaursStatus.MEANING_NOT_FOUND, 42L);

        assertThat(suggestion.getCorpusStatus()).isEqualTo(CheckStatus.NOT_FOUND);
        suggestion.changeStatus(SuggestionStatus.COMPLETED);

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.COMPLETED);
    }

    private static Suggestion suggestion() {
        return new Suggestion("submitted", null, null, null, null);
    }
}
