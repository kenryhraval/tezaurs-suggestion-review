package lv.tezaurs.suggestions.suggestion.entity;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lv.tezaurs.suggestions.common.error.ConflictException;
import lv.tezaurs.suggestions.usage.entity.UsageDecision;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "cases", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCase {
    @Id
    private UUID id;

    @Column(name = "reviewed_term", nullable = false, columnDefinition = "text")
    private String reviewedTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewStatus status;

    @Column(name = "last_status_changed_by")
    private String lastStatusChangedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CaseType type;

    @Column(name = "matched_entry_id")
    private Integer matchedEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_decision", nullable = false, length = 32)
    private UsageDecision usageDecision = UsageDecision.NOT_CHECKED;

    @Column(name = "usage_note", columnDefinition = "text")
    private String usageNote;

    @Column(name = "usage_decided_by")
    private String usageDecidedBy;

    @Column(name = "usage_decided_at")
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

    @OneToMany(mappedBy = "reviewCase", fetch = FetchType.LAZY)
    private List<CaseSuggestion> sources = new ArrayList<>();

    public ReviewCase(String submittedTerm, String username) {
        this(submittedTerm, ReviewStatus.NEW);
        this.lastStatusChangedBy = requireStatusAuthor(username);
    }

    private ReviewCase(String submittedTerm, ReviewStatus initialStatus) {
        this.id = UUID.randomUUID();
        this.reviewedTerm = submittedTerm;
        this.status = initialStatus;
        this.type = CaseType.UNKNOWN;
    }

    public static ReviewCase fromSuggestion(String submittedTerm, ReviewStatus initialStatus) {
        return new ReviewCase(submittedTerm, initialStatus);
    }

    void addSource(CaseSuggestion source) {
        sources.add(source);
    }

    public void changeStatus(ReviewStatus newStatus, String username) {
        status.requireTransitionTo(newStatus);
        if (newStatus == ReviewStatus.ALREADY_EXISTS) {
            if (type != CaseType.SENSE || matchedEntryId == null) {
                throw new ConflictException("Select the matching Tēzaurs entry");
            }
        }
        updateStatus(newStatus, username);
    }

    public void takeForReview(String username) {
        if (status != ReviewStatus.IN_PROGRESS) {
            changeStatus(ReviewStatus.IN_PROGRESS, username);
        } else if (lastStatusChangedBy == null) {
            lastStatusChangedBy = requireStatusAuthor(username);
        }
    }

    public void correctTerm(String term) {
        if (status != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Resume the case in Apstrādē before changing its title");
        }
        reviewedTerm = term;
    }

    public void recordUsageDecision(UsageDecision decision, String note, String username) {
        if (type != CaseType.ENTRY || status != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Usage decisions require a new-entry case in progress");
        }
        if (decision == UsageDecision.NOT_CHECKED) {
            throw new ConflictException("Choose whether usage has been confirmed");
        }
        usageDecision = decision;
        usageNote = note == null || note.isBlank() ? null : note.trim();
        usageDecidedBy = username;
        usageDecidedAt = Instant.now();
        if (decision == UsageDecision.NOT_CONFIRMED) {
            updateStatus(ReviewStatus.INSUFFICIENT_DATA, username);
        } else if (decision == UsageDecision.INVENTED) {
            updateStatus(ReviewStatus.INVENTED, username);
        }
    }

    public void changeType(CaseType selectedType, Integer selectedEntryId,
            boolean meaningAlreadyExists, String username) {
        requireStatusAuthor(username);
        if (status != ReviewStatus.IN_PROGRESS && status != ReviewStatus.ALREADY_EXISTS) {
            throw new ConflictException("Resume the case in Apstrādē before changing its type");
        }
        if (selectedType == null) {
            throw new ConflictException("Choose the case type");
        }
        if (selectedType == CaseType.SENSE && selectedEntryId == null) {
            throw new ConflictException("Select the matching Tēzaurs entry");
        }
        if (selectedType != CaseType.SENSE && selectedEntryId != null) {
            throw new ConflictException("Only a sense case can reference an existing Tēzaurs entry");
        }
        if (meaningAlreadyExists && selectedType != CaseType.SENSE) {
            throw new ConflictException("An existing meaning requires a matching Tēzaurs entry");
        }

        type = selectedType;
        matchedEntryId = selectedEntryId;
        if (meaningAlreadyExists) {
            if (status != ReviewStatus.ALREADY_EXISTS) {
                changeStatus(ReviewStatus.ALREADY_EXISTS, username);
            }
        } else if (status == ReviewStatus.ALREADY_EXISTS) {
            updateStatus(ReviewStatus.IN_PROGRESS, username);
            resetUsageDecision();
        }
    }

    private void resetUsageDecision() {
        usageDecision = UsageDecision.NOT_CHECKED;
        usageNote = null;
        usageDecidedBy = null;
        usageDecidedAt = null;
    }

    private void updateStatus(ReviewStatus newStatus, String username) {
        if (status != newStatus) {
            String author = requireStatusAuthor(username);
            status = newStatus;
            lastStatusChangedBy = author;
        }
    }

    private static String requireStatusAuthor(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("A status author is required");
        }
        return username;
    }

    public boolean canEditEntryDraft() {
        return status == ReviewStatus.IN_PROGRESS && type == CaseType.ENTRY
                && usageDecision == UsageDecision.USED;
    }
}
