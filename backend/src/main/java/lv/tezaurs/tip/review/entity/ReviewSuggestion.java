package lv.tezaurs.tip.review.entity;

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
import lv.tezaurs.tip.suggestion.entity.IncubatorSuggestion;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "case_suggestions", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSuggestion {
    @Id
    @Column(name = "source_suggestion_id")
    private Integer sourceSuggestionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_suggestion_id")
    private IncubatorSuggestion sourceSuggestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private Review review;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReviewSuggestion(
            IncubatorSuggestion sourceSuggestion, Review review) {
        this.sourceSuggestion = sourceSuggestion;
        this.review = review;
        review.getSuggestions().add(this);
    }

    public void reassignTo(Review target) {
        review.getSuggestions().remove(this);
        review = target;
        target.getSuggestions().add(this);
    }

}
