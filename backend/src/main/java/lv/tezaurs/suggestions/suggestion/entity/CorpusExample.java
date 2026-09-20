package lv.tezaurs.suggestions.suggestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "corpus_example")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorpusExample {
    @Id
    private UUID id;

    @Column(name = "suggestion_id", nullable = false, updatable = false)
    private UUID suggestionId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String url;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CorpusExample(UUID suggestionId, String url) {
        this.id = UUID.randomUUID();
        this.suggestionId = suggestionId;
        this.url = url;
    }
}
