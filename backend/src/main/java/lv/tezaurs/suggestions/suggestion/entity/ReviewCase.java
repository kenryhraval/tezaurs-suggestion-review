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

    @Column(name = "reviewed_term", nullable = false)
    private String reviewedTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewStatus status;

    @Column(name = "last_status_changed_by", nullable = false)
    private String lastStatusChangedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "tezaurs_status", nullable = false, length = 32)
    private TezaursStatus tezaursStatus;

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
        this.lastStatusChangedBy = requireStatusAuthor(username);
        this.id = UUID.randomUUID();
        this.reviewedTerm = submittedTerm;
        this.status = ReviewStatus.NEW;
        this.tezaursStatus = TezaursStatus.NOT_CHECKED;
    }

    void addSource(CaseSuggestion source) {
        sources.add(source);
    }

    public void changeStatus(ReviewStatus newStatus, String username) {
        status.requireTransitionTo(newStatus);
        if (newStatus == ReviewStatus.ALREADY_EXISTS) {
            if (matchedEntryId == null) {
                throw new ConflictException("Select the matching Tēzaurs entry");
            }
            tezaursStatus = TezaursStatus.MEANING_ALREADY_EXISTS;
        }
        updateStatus(newStatus, username);
    }

    public void takeForReview(String username) {
        if (status != ReviewStatus.IN_PROGRESS) {
            changeStatus(ReviewStatus.IN_PROGRESS, username);
        }
    }

    public void correctTerm(String term) {
        if (status != ReviewStatus.IN_PROGRESS) {
            throw new ConflictException("Resume the case in Apstrādē before changing its title");
        }
        reviewedTerm = term;
    }

    public void recordUsageDecision(UsageDecision decision, String note, String username) {
        if (tezaursStatus != TezaursStatus.NOT_FOUND
                || (status != ReviewStatus.IN_PROGRESS && status != ReviewStatus.INSUFFICIENT_DATA
                    && status != ReviewStatus.INVENTED)) {
            throw new ConflictException("Usage decisions require a new-entry case in progress or awaiting evidence");
        }
        if (decision == UsageDecision.NOT_CHECKED) {
            throw new ConflictException("Choose whether usage has been confirmed");
        }
        usageDecision = decision;
        usageNote = note == null || note.isBlank() ? null : note.trim();
        usageDecidedBy = username;
        usageDecidedAt = Instant.now();
        if (decision == UsageDecision.USED) {
            updateStatus(ReviewStatus.IN_PROGRESS, username);
        } else if (decision == UsageDecision.NOT_CONFIRMED) {
            updateStatus(ReviewStatus.INSUFFICIENT_DATA, username);
        } else {
            updateStatus(ReviewStatus.INVENTED, username);
        }
    }

    public void recordTezaursCheck(TezaursStatus newTezaursStatus, Integer selectedEntryId, String username) {
        if (status != ReviewStatus.IN_PROGRESS && status != ReviewStatus.ALREADY_EXISTS) {
            throw new ConflictException("Resume the case in Apstrādē before changing the Tēzaurs check");
        }
        if (newTezaursStatus.entryExists() && selectedEntryId == null) {
            throw new ConflictException("Select the matching Tēzaurs entry");
        }
        if (!newTezaursStatus.entryExists() && selectedEntryId != null) {
            throw new ConflictException("An entry ID is only allowed when a Tēzaurs entry was found");
        }
        boolean shouldReopenCase = this.status == ReviewStatus.ALREADY_EXISTS
                && newTezaursStatus != TezaursStatus.MEANING_ALREADY_EXISTS;
        if (shouldReopenCase) {
            updateStatus(ReviewStatus.IN_PROGRESS, username);
            resetUsageDecision();
        }
        this.tezaursStatus = newTezaursStatus;
        this.matchedEntryId = selectedEntryId;
        if (newTezaursStatus == TezaursStatus.MEANING_ALREADY_EXISTS
                && status != ReviewStatus.ALREADY_EXISTS) {
            changeStatus(ReviewStatus.ALREADY_EXISTS, username);
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
        return status == ReviewStatus.IN_PROGRESS && tezaursStatus == TezaursStatus.NOT_FOUND
                && usageDecision == UsageDecision.USED;
    }
}
