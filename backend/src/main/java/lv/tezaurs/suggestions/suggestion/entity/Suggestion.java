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
    private TezaursStatus tezaursStatus;

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
        this.tezaursStatus = TezaursStatus.NOT_CHECKED;
        this.corpusStatus = CheckStatus.NOT_CHECKED;
    }

    public void changeStatus(SuggestionStatus newStatus) {
        if (newStatus == SuggestionStatus.COMPLETED && !canComplete()) {
            throw new ConflictException("All required review steps must be completed first");
        }
        status = newStatus;
    }

    public boolean canComplete() {
        return completionBlockReason() == null;
    }

    public CompletionBlockReason completionBlockReason() {
        return switch (tezaursStatus) {
            case NOT_CHECKED -> CompletionBlockReason.TEZAURS_CHECK_REQUIRED;
            case FOUND -> CompletionBlockReason.MEANING_CHECK_REQUIRED;
            case MEANING_FOUND -> null;
            case MEANING_NOT_FOUND, NOT_FOUND -> corpusStatus == CheckStatus.NOT_CHECKED
                    ? CompletionBlockReason.CORPUS_CHECK_REQUIRED
                    : null;
        };
    }

    public void correctTerm(String term) {
        reviewedTerm = term;
    }

    public void recordTezaursCheck(TezaursStatus checkStatus, Long entryId) {
        if (!checkStatus.entryExists() && entryId != null) {
            throw new ConflictException("An entry ID is only allowed when a Tēzaurs entry was found");
        }
        TezaursStatus previousStatus = tezaursStatus;
        tezaursStatus = checkStatus;
        matchedEntryId = entryId;

        if ((checkStatus == TezaursStatus.MEANING_NOT_FOUND
                || checkStatus == TezaursStatus.NOT_FOUND)
                && corpusStatus == CheckStatus.NOT_CHECKED) {
            corpusStatus = CheckStatus.NOT_FOUND;
        }

        if (checkStatus == TezaursStatus.MEANING_FOUND) {
            status = SuggestionStatus.COMPLETED;
        } else if (previousStatus == TezaursStatus.MEANING_FOUND
                && status == SuggestionStatus.COMPLETED) {
            status = SuggestionStatus.IN_PROGRESS;
        }
    }

    public void recordCorpusExample() {
        if (tezaursStatus != TezaursStatus.MEANING_NOT_FOUND
                && tezaursStatus != TezaursStatus.NOT_FOUND) {
            throw new ConflictException("Corpus evidence is only applicable when the submitted meaning is absent");
        }
        corpusStatus = CheckStatus.FOUND;
    }
}
