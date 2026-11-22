package lv.tezaurs.tip.sense.entity;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.example.entity.Example;
import lv.tezaurs.tip.example.entity.ExampleSource;
import org.junit.jupiter.api.Test;

class SenseCandidateLifecycleTest {
    @Test
    void approvalKeepsTheSameSenseAndExamples() {
        UUID reviewId = UUID.randomUUID();
        var example = example(reviewId);
        var candidate = candidate(reviewId);
        var id = candidate.getId();
        example.reassignTo(candidate);
        assertThat(candidate.isApprovedDraft()).isFalse();
        candidate.setApproved(true);
        assertThat(candidate.getId()).isEqualTo(id);
        assertThat(candidate.isApprovedDraft()).isTrue();
        assertThat(example.getSense()).isSameAs(candidate);
    }

    @Test
    void movingAnExampleOutOfACandidateUpdatesBothSides() {
        UUID reviewId = UUID.randomUUID();
        var example = example(reviewId);
        var candidate = candidate(reviewId);
        example.reassignTo(candidate);
        var draft = new Sense(reviewId);
        example.reassignTo(draft);
        assertThat(candidate.getExamples()).isEmpty();
        assertThat(example.getSense()).isSameAs(draft);
        var nextSense = new Sense(reviewId);
        example.reassignTo(nextSense);
        assertThat(draft.getExamples()).isEmpty();
        assertThat(example.getSense()).isSameAs(nextSense);
    }

    @Test
    void rejectsForeignReviewsAndNeverTreatsAnExistingReferenceAsANewDraft() {
        UUID reviewId = UUID.randomUUID();
        var example = example(reviewId);
        assertThatThrownBy(() -> example.reassignTo(candidate(UUID.randomUUID())))
                .isInstanceOf(ConflictException.class);
        var reference = new Sense(UUID.randomUUID(), reviewId, 100, "Esoša nozīme", 10, 1);
        reference.setApproved(true);
        assertThat(reference.isApprovedDraft()).isFalse();
    }

    private Sense candidate(UUID reviewId) {
        return new Sense(UUID.randomUUID(), reviewId, null, "Apraksts", null, 1);
    }

    private Example example(UUID reviewId) {
        return new Example(reviewId, new SaveExampleRequest(ExampleSource.EXTERNAL, null, null,
                "vārds", "Piemērs", "https://example.com", null, null), "editor");
    }
}
