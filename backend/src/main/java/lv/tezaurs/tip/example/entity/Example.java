package lv.tezaurs.tip.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lv.tezaurs.tip.common.error.ConflictException;
import lv.tezaurs.tip.corpus.dto.Corpus;
import lv.tezaurs.tip.example.dto.SaveExampleRequest;
import lv.tezaurs.tip.sense.entity.Sense;

@Entity
@Table(name = "examples", schema = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Example {
    @Id
    private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sense_id")
    private Sense sense;

    @Column(nullable = false)
    @Setter
    private boolean approved;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExampleSource source;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Corpus corpus;
    @Column(name = "token_number")
    private Long tokenNumber;
    @Column(name = "search_query", nullable = false)
    private String query;
    @Column(nullable = false, columnDefinition = "text")
    private String citation;
    @Column(name = "match_start")
    private Integer matchStart;
    @Column(name = "match_end")
    private Integer matchEnd;
    @Column(nullable = false, columnDefinition = "text")
    private String url;
    @Column(columnDefinition = "text")
    private String reference;
    @Column(columnDefinition = "text")
    private String note;
    @Column(name = "created_by")
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Example(UUID reviewId, SaveExampleRequest request, String username) {
        id = UUID.randomUUID();
        this.reviewId = reviewId;
        source = request.source();
        corpus = request.corpus();
        tokenNumber = request.tokenNumber();
        query = request.query().trim();
        citation = request.citation().trim();
        matchStart = request.matchStart();
        matchEnd = request.matchEnd();
        url = request.url().trim();
        reference = request.reference();
        note = request.note();
        createdBy = username;
        createdAt = Instant.now();
    }

    public void reassignTo(Sense targetSense) {
        if (targetSense != null && !reviewId.equals(targetSense.getReviewId())) {
            throw new ConflictException("Piemēram un nozīmei jāpieder vienam perēklim.");
        }
        if (sense == targetSense) {
            return;
        }

        if (sense != null) {
            sense.getExamples().remove(this);
        }
        sense = targetSense;
        approved = false;
        if (targetSense != null && !targetSense.getExamples().contains(this)) {
            targetSense.getExamples().add(this);
        }
    }

}
