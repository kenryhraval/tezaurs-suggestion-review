package lv.tezaurs.suggestions.suggestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "case_suggestions", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseSuggestion {
    @Id
    @Column(name = "source_suggestion_id")
    private Integer sourceSuggestionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_suggestion_id")
    private IncubatorSuggestion sourceSuggestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private ReviewCase reviewCase;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CaseSuggestion(
            IncubatorSuggestion sourceSuggestion, ReviewCase reviewCase) {
        this.sourceSuggestion = sourceSuggestion;
        this.reviewCase = reviewCase;
        reviewCase.addSource(this);
    }
}
