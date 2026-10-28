package lv.tezaurs.suggestions.entry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "lexemes", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLexeme {
    @Id
    private UUID id;

    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(nullable = false)
    private String lemma;

    @Column(name = "primary_lexeme", nullable = false)
    private boolean primary;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "paradigm_id")
    private Integer paradigmId;

    @Column(columnDefinition = "text")
    private String stem1;

    @Column(columnDefinition = "text")
    private String stem2;

    @Column(columnDefinition = "text")
    private String stem3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> pronunciations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> flags = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structural_restrictions", columnDefinition = "jsonb")
    private Map<String, Object> structuralRestrictions;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ReviewLexeme(UUID entryId, int orderNo) {
        this.id = UUID.randomUUID();
        this.entryId = entryId;
        this.orderNo = orderNo;
    }

    public void update(String lemma, boolean primary, Integer roleId, Integer paradigmId,
                       String stem1, String stem2, String stem3, List<String> pronunciations,
                       Map<String, Object> flags, Map<String, Object> structuralRestrictions,
                       int orderNo) {
        this.lemma = lemma;
        this.primary = primary;
        this.roleId = roleId;
        this.paradigmId = paradigmId;
        this.stem1 = stem1;
        this.stem2 = stem2;
        this.stem3 = stem3;
        this.pronunciations = new ArrayList<>(pronunciations);
        this.flags = new HashMap<>(flags);
        this.structuralRestrictions = structuralRestrictions == null
                ? null : new HashMap<>(structuralRestrictions);
        this.orderNo = orderNo;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
