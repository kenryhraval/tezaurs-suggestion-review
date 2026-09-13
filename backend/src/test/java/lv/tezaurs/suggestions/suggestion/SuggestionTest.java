package lv.tezaurs.suggestions.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import lv.tezaurs.suggestions.suggestion.entity.Suggestion;
import lv.tezaurs.suggestions.suggestion.entity.SuggestionStatus;
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

    private static Suggestion suggestion() {
        return new Suggestion(UUID.randomUUID(), "submitted", "definition", null, null, null, null);
    }
}
