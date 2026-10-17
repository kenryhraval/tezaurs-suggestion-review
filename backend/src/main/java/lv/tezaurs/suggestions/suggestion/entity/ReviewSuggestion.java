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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "suggestions", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSuggestion {
    @Id
    private UUID id;

    @Column(name = "reviewed_term", nullable = false)
    private String reviewedTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tezaurs_status", nullable = false, length = 32)
    private TezaursStatus tezaursStatus;

    @Column(name = "matched_entry_id")
    private Integer matchedEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "corpus_status", nullable = false, length = 32)
    private CheckStatus corpusStatus;

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
    private List<ReviewSuggestionSource> sources = new ArrayList<>();

    public ReviewSuggestion(String submittedTerm) {
        this.id = UUID.randomUUID();
        this.reviewedTerm = submittedTerm;
        this.status = SuggestionStatus.NEW;
        this.tezaursStatus = TezaursStatus.NOT_CHECKED;
        this.corpusStatus = CheckStatus.NOT_CHECKED;
    }

    void addSource(ReviewSuggestionSource source) {
        sources.add(source);
    }

    public void changeStatus(SuggestionStatus newStatus) {
        status.requireTransitionTo(newStatus);
        if (newStatus == SuggestionStatus.ALREADY_EXISTS
                && tezaursStatus != TezaursStatus.MEANING_FOUND) {
            throw new ConflictException("The submitted meaning must be confirmed in Tēzaurs first");
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

    public void recordTezaursCheck(TezaursStatus checkStatus, Integer entryId) {
        if (!checkStatus.entryExists() && entryId != null) {
            throw new ConflictException("An entry ID is only allowed when a Tēzaurs entry was found");
        }
        tezaursStatus = checkStatus;
        matchedEntryId = entryId;

        if ((checkStatus == TezaursStatus.MEANING_NOT_FOUND
                || checkStatus == TezaursStatus.NOT_FOUND)
                && corpusStatus == CheckStatus.NOT_CHECKED) {
            corpusStatus = CheckStatus.NOT_FOUND;
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
