package lv.tezaurs.tip.entry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "entries", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Entry {
    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true, updatable = false)
    private UUID reviewId;

    @Column(nullable = false)
    private String heading;

    @Column(name = "homonym_no", nullable = false)
    private int homonymNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntryType type;

    @Column(columnDefinition = "text")
    private String etymology;

    @Column(name = "normative_comment", columnDefinition = "text")
    private String normativeComment;

    @Column(name = "scientific_name", columnDefinition = "text")
    private String scientificName;

    @Column(columnDefinition = "text")
    private String source;

    @Column(columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> flags = new HashMap<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Entry(UUID reviewId, String heading, int homonymNo) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.heading = heading;
        this.homonymNo = homonymNo;
        this.type = EntryType.WORD;
    }

    public void update(String heading, int homonymNo, EntryType type,
                       String etymology, String normativeComment, String scientificName,
                       String source, String notes, Map<String, Object> flags) {
        this.heading = heading;
        this.homonymNo = homonymNo;
        this.type = type;
        this.etymology = etymology;
        this.normativeComment = normativeComment;
        this.scientificName = scientificName;
        this.source = source;
        this.notes = notes;
        this.flags = new HashMap<>(flags);
    }

    public String humanKey() {
        return heading + ":" + homonymNo;
    }

}
