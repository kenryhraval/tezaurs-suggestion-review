package lv.tezaurs.suggestions.tezaurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "senses", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursSense {
    @Id
    private Integer id;

    @Column(name = "entry_id", nullable = false, updatable = false)
    private Integer entryId;

    @Column(name = "parent_sense_id", updatable = false)
    private Integer parentSenseId;

    @Column(updatable = false)
    private String gloss;

    @Column(name = "order_no", updatable = false)
    private Integer orderNo;
}
