package lv.tezaurs.suggestions.sense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "senses", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSense {
    @Id
    private UUID id;

    @Column(name = "suggestion_id", nullable = false, updatable = false)
    private UUID suggestionId;

    @Column(name = "parent_draft_sense_id")
    private UUID parentDraftSenseId;

    @Column(name = "parent_tezaurs_sense_id")
    private Integer parentTezaursSenseId;

    @Column(nullable = false, columnDefinition = "text")
    private String gloss;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> flags = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structural_restrictions", columnDefinition = "jsonb")
    private Map<String, Object> structuralRestrictions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ReviewSense(UUID suggestionId) {
        this.id = UUID.randomUUID();
        this.suggestionId = suggestionId;
    }

    public void update(String gloss, UUID parentDraftSenseId, Integer parentTezaursSenseId,
                       int orderNo, Map<String, Object> flags,
                       Map<String, Object> structuralRestrictions) {
        this.gloss = gloss;
        this.parentDraftSenseId = parentDraftSenseId;
        this.parentTezaursSenseId = parentTezaursSenseId;
        this.orderNo = orderNo;
        this.flags = new HashMap<>(flags);
        this.structuralRestrictions = structuralRestrictions == null
                ? null : new HashMap<>(structuralRestrictions);
    }
}
