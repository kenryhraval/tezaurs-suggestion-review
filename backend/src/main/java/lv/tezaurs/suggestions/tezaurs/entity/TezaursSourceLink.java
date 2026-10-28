package lv.tezaurs.suggestions.tezaurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "source_links", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursSourceLink {
    @Id
    private Integer id;

    @Column(name = "entry_id", nullable = false, updatable = false)
    private Integer entryId;

    @Column(name = "sense_id", updatable = false)
    private Integer senseId;

    @Column(name = "lexeme_id", updatable = false)
    private Integer lexemeId;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Integer sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", insertable = false, updatable = false)
    private TezaursSource source;

    @Column(name = "order_no", updatable = false)
    private Integer orderNo;
}
