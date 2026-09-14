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
    void recordsManualDictionaryAndCorpusChecks() {
        Suggestion suggestion = suggestion();

        suggestion.recordTezaursCheck(CheckStatus.FOUND, 42L);
        suggestion.recordCorpusCheck(CheckStatus.NOT_FOUND);

        assertThat(suggestion.getTezaursStatus()).isEqualTo(CheckStatus.FOUND);
        assertThat(suggestion.getMatchedEntryId()).isEqualTo(42L);
        assertThat(suggestion.getCorpusStatus()).isEqualTo(CheckStatus.NOT_FOUND);

        suggestion.recordTezaursCheck(CheckStatus.NOT_FOUND, null);

        assertThat(suggestion.getTezaursStatus()).isEqualTo(CheckStatus.NOT_FOUND);
        assertThat(suggestion.getMatchedEntryId()).isNull();
    }

    @Test
    void requiresAnEntryIdOnlyForAFoundDictionaryEntry() {
        Suggestion suggestion = suggestion();

        assertThatThrownBy(() -> suggestion.recordTezaursCheck(CheckStatus.FOUND, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A found Tēzaurs entry requires its ID");
        assertThatThrownBy(() -> suggestion.recordTezaursCheck(CheckStatus.NOT_CHECKED, 42L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An entry ID is only allowed when a Tēzaurs entry was found");
    }

    private static Suggestion suggestion() {
        return new Suggestion("submitted", null, null, null, null);
    }
}
