package lv.tezaurs.tip.sense.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SenseTest {
    @Test
    void onlyApprovedNewSensesAreApprovedDrafts() {
        UUID reviewId = UUID.randomUUID();
        Sense draft = new Sense(reviewId);
        Sense reference = new Sense(UUID.randomUUID(), reviewId, 100, "Esoša nozīme", 10, 1);

        assertThat(draft.isApprovedDraft()).isFalse();
        draft.setApproved(true);
        reference.setApproved(true);

        assertThat(draft.isApprovedDraft()).isTrue();
        assertThat(reference.isApprovedDraft()).isFalse();
    }
}
