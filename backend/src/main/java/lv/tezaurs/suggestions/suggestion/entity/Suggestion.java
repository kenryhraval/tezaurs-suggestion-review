package lv.tezaurs.suggestions.suggestion.entity;

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
@Table(name = "suggestion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suggestion {
    @Id
    private UUID id;

    @Column(name = "submitted_term", nullable = false, updatable = false)
    private String submittedTerm;

    @Column(name = "reviewed_term")
    private String reviewedTerm;

    @Column(name = "usage_example", columnDefinition = "text")
    private String usageExample;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "submitter_name")
    private String submitterName;

    @Column(name = "submitter_email", length = 320)
    private String submitterEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tezaurs_status", nullable = false, length = 32)
    private CheckStatus tezaursStatus;

    @Column(name = "matched_entry_id")
    private Long matchedEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "corpus_status", nullable = false, length = 32)
    private CheckStatus corpusStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Suggestion(String submittedTerm, String usageExample, String notes,
                      String submitterName, String submitterEmail) {
        this.id = UUID.randomUUID();
        this.submittedTerm = submittedTerm;
        this.usageExample = usageExample;
        this.notes = notes;
        this.submitterName = submitterName;
        this.submitterEmail = submitterEmail;
        this.status = SuggestionStatus.NEW;
        this.tezaursStatus = CheckStatus.NOT_CHECKED;
        this.corpusStatus = CheckStatus.NOT_CHECKED;
    }

    public void changeStatus(SuggestionStatus newStatus) {
        status = newStatus;
    }

    public void correctTerm(String term) {
        reviewedTerm = term;
    }

    public void recordTezaursCheck(CheckStatus checkStatus, Long entryId) {
        if (checkStatus == CheckStatus.FOUND && entryId == null) {
            throw new ConflictException("A found Tēzaurs entry requires its ID");
        }
        if (checkStatus != CheckStatus.FOUND && entryId != null) {
            throw new ConflictException("An entry ID is only allowed when a Tēzaurs entry was found");
        }
        tezaursStatus = checkStatus;
        matchedEntryId = entryId;
    }

    public void recordCorpusCheck(CheckStatus checkStatus) {
        corpusStatus = checkStatus;
    }
}
