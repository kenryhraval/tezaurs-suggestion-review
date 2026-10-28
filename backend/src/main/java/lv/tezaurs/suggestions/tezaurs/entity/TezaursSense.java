package lv.tezaurs.suggestions.tezaurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "sense_tag", updatable = false)
    private String senseTag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> data;

    @Column(updatable = false)
    private boolean hidden;

    @Column(name = "order_no", updatable = false)
    private Integer orderNo;
}
