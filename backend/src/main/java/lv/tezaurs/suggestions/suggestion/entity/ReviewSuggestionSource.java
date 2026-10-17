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
@Table(name = "suggestion_sources", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSuggestionSource {
    @Id
    @Column(name = "source_suggestion_id")
    private Integer sourceSuggestionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_suggestion_id")
    private IncubatorSuggestion sourceSuggestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggestion_id", nullable = false, updatable = false)
    private ReviewSuggestion review;

    @Column(name = "primary_source", nullable = false, updatable = false)
    private boolean primarySource;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReviewSuggestionSource(
            IncubatorSuggestion sourceSuggestion, ReviewSuggestion review, boolean primarySource) {
        this.sourceSuggestion = sourceSuggestion;
        this.review = review;
        this.primarySource = primarySource;
        review.addSource(this);
    }
}
