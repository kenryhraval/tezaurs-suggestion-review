package lv.tezaurs.suggestions.suggestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "suggestions", schema = "incubator")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncubatorSuggestion {
    @Id
    private Integer id;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String lemma;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String gloss;

    @Column(columnDefinition = "text", updatable = false)
    private String example;

    @Column(columnDefinition = "text", updatable = false)
    private String source;

    @Column(name = "flag_info", columnDefinition = "text", updatable = false)
    private String flagInfo;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    @Column(columnDefinition = "text", updatable = false)
    private String contact;

    @Column(name = "created_by", updatable = false)
    private Integer createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", updatable = false)
    private Integer updatedBy;

    public IncubatorSuggestion(Integer id, String lemma, String gloss) {
        this.id = id;
        this.lemma = lemma;
        this.gloss = gloss;
        this.createdAt = Instant.now();
    }
}
