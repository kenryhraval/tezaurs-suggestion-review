package lv.tezaurs.tip.example.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.sense.entity.Sense;
import lv.tezaurs.tip.example.entity.ExampleSource;
import org.junit.jupiter.api.Test;

class ExampleTest {
    private Example createExternalExample(UUID reviewId, String citation) {
        return new Example(reviewId, new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", citation, "https://example.com", null, null, null, null), "editor");
    }

    @Test
    void assigningAnExampleTwiceDoesNotDuplicateIt() {
        UUID reviewId = UUID.randomUUID();
        Sense sense = new Sense(reviewId);
        Example first = createExternalExample(reviewId, "Pirmais piemērs");
        Example second = createExternalExample(reviewId, "Otrais piemērs");
        assertThat(first.getSense()).isNull();

        first.reassignTo(sense);
        second.reassignTo(sense);
        first.reassignTo(sense);

        assertThat(sense.getExamples()).containsExactly(first, second);
        assertThat(first.getSense()).isSameAs(sense);
        assertThat(second.getSense()).isSameAs(sense);
    }

    @Test
    void reassignmentUpdatesBothSenseCollections() {
        UUID reviewId = UUID.randomUUID();
        Sense firstSense = new Sense(reviewId);
        Sense secondSense = new Sense(reviewId);
        Example example = createExternalExample(reviewId, "Piemērs");
        firstSense.setApproved(true);
        secondSense.setApproved(true);
        example.reassignTo(firstSense);
        example.reassignTo(secondSense);

        assertThat(example.getSense()).isSameAs(secondSense);
        assertThat(firstSense.getExamples()).isEmpty();
        assertThat(secondSense.getExamples()).containsExactly(example);
    }

    @Test
    void detachingKeepsTheExampleAvailableForReassignment() {
        UUID reviewId = UUID.randomUUID();
        Sense sense = new Sense(reviewId);
        Example example = createExternalExample(reviewId, "Piemērs");
        example.reassignTo(sense);

        example.reassignTo(null);

        assertThat(sense.getExamples()).isEmpty();
        assertThat(example.getSense()).isNull();
        assertThat(example.getCitation()).isEqualTo("Piemērs");
        Sense nextSense = new Sense(reviewId);
        example.reassignTo(nextSense);
        assertThat(example.getSense()).isSameAs(nextSense);
    }

    @Test
    void rejectsASenseFromAnotherReview() {
        Sense sense = new Sense(UUID.randomUUID());
        Example example = createExternalExample(UUID.randomUUID(), "Piemērs");

        assertThatThrownBy(() -> example.reassignTo(sense)).isInstanceOf(ConflictException.class);
        assertThat(example.getSense()).isNull();
        assertThat(sense.getExamples()).isEmpty();
    }

}
