package lv.tezaurs.tip.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "cases", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {
    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "text")
    private String publicId;

    @Column(name = "reviewed_term", nullable = false, columnDefinition = "text")
    @Setter
    private String reviewedTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Setter
    private ReviewStatus status;

    @Column(name = "last_status_changed_by")
    @Setter
    private String lastStatusChangedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    private ReviewType type;

    @Column(name = "matched_entry_id")
    @Setter
    private Integer matchedEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_decision", length = 32)
    @Setter
    private UsageDecision usageDecision;

    @Column(name = "usage_note", columnDefinition = "text")
    @Setter
    private String usageNote;

    @Column(name = "usage_decided_by")
    @Setter
    private String usageDecidedBy;

    @Column(name = "usage_decided_at")
    @Setter
    private Instant usageDecidedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "review", fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    private List<ReviewSuggestion> suggestions = new ArrayList<>();

    public Review(String submittedTerm, String username, String publicId) {
        this(submittedTerm, ReviewStatus.NEW, publicId);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("A status author is required");
        }
        this.lastStatusChangedBy = username;
    }

    public Review(String submittedTerm, ReviewStatus initialStatus, String publicId) {
        this.id = UUID.randomUUID();
        this.publicId = Objects.requireNonNull(publicId);
        this.reviewedTerm = submittedTerm;
        this.status = initialStatus;
        this.type = ReviewType.UNKNOWN;
    }

}
