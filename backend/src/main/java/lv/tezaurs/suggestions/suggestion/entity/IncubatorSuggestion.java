package lv.tezaurs.suggestions.suggestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
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

    @Convert(converter = SuggestionStatusConverter.class)
    @Column(nullable = false)
    private SuggestionStatus status;

    @Column(name = "channel_id", nullable = false, updatable = false)
    private Integer channelId;

    @Column(name = "updated_by", updatable = false)
    private Integer updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public IncubatorSuggestion(Integer id, String lemma, String gloss, SuggestionStatus status) {
        this.id = id;
        this.lemma = lemma;
        this.gloss = gloss;
        this.status = status;
        this.channelId = 1;
        this.createdAt = Instant.now();
    }

    public void changeStatus(SuggestionStatus newStatus) {
        status.requireTransitionTo(newStatus);
        status = newStatus;
        updatedAt = Instant.now();
    }
}
