package lv.tezaurs.tip.sense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lv.tezaurs.tip.example.entity.Example;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "senses", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sense {
    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Column(name = "target_entry_id", updatable = false)
    private Integer targetEntryId;

    @Column(nullable = false)
    @Setter
    private boolean approved;

    @Column(name = "existing_sense_id", updatable = false)
    private Integer existingSenseId;

    @Column(name = "parent_sense_id")
    @Setter
    private UUID parentSenseId;

    @Column(nullable = false, columnDefinition = "text")
    @Setter
    private String gloss;

    @Column(name = "order_no", nullable = false)
    @Setter
    private int orderNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Setter
    private Map<String, Object> flags = new HashMap<>();

    @OneToMany(mappedBy = "sense")
    @BatchSize(size = 100)
    private List<Example> examples = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Sense(UUID reviewId) {
        this(reviewId, null);
    }

    public Sense(UUID reviewId, Integer targetEntryId) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.targetEntryId = targetEntryId;
    }

    public Sense(UUID id, UUID reviewId, Integer targetEntryId, String description,
            Integer existingSenseId, int orderNo) {
        this.id = id;
        this.reviewId = reviewId;
        this.targetEntryId = targetEntryId;
        this.gloss = description.trim();
        this.approved = false;
        this.existingSenseId = existingSenseId;
        this.orderNo = orderNo;
    }

    public boolean isApprovedDraft() {
        return approved && existingSenseId == null;
    }

}
