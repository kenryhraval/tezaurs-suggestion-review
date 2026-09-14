package lv.tezaurs.suggestions.meaning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "meaning")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meaning {
    @Id
    private UUID id;

    @Column(name = "suggestion_id", nullable = false, updatable = false)
    private UUID suggestionId;

    @Column(name = "parent_meaning_id", updatable = false)
    private UUID parentMeaningId;

    @Column(name = "supersedes_meaning_id", updatable = false)
    private UUID supersedesMeaningId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private MeaningOrigin origin;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String gloss;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MeaningStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Meaning(UUID suggestionId, UUID parentMeaningId, MeaningOrigin origin, String gloss) {
        this(UUID.randomUUID(), suggestionId, parentMeaningId, null, origin, gloss,
                origin == MeaningOrigin.EXISTING ? MeaningStatus.APPROVED : MeaningStatus.PROPOSED);
    }

    private Meaning(UUID id, UUID suggestionId, UUID parentMeaningId, UUID supersedesMeaningId,
                    MeaningOrigin origin, String gloss, MeaningStatus status) {
        this.id = id;
        this.suggestionId = suggestionId;
        this.parentMeaningId = parentMeaningId;
        this.supersedesMeaningId = supersedesMeaningId;
        this.origin = origin;
        this.gloss = gloss;
        this.status = status;
    }

    public void approve() {
        ensureEditable();
        status = MeaningStatus.APPROVED;
    }

    public void reject() {
        ensureEditable();
        status = MeaningStatus.REJECTED;
    }

    public void supersede() {
        ensureEditable();
        status = MeaningStatus.SUPERSEDED;
    }

    public Meaning revise(String revisedGloss) {
        supersede();
        return new Meaning(UUID.randomUUID(), suggestionId, parentMeaningId, id, MeaningOrigin.REVIEWER,
                revisedGloss, MeaningStatus.PROPOSED);
    }

    private void ensureEditable() {
        if (status == MeaningStatus.SUPERSEDED) {
            throw new ConflictException("A superseded meaning cannot be changed");
        }
    }
}
