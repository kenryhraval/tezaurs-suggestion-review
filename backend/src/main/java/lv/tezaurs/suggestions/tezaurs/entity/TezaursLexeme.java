package lv.tezaurs.suggestions.tezaurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "lexemes", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursLexeme {
    @Id
    private Integer id;

    @Column(name = "entry_id", updatable = false)
    private Integer entryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", insertable = false, updatable = false)
    private TezaursEntry entry;

    @Column(updatable = false)
    private String lemma;

    @Column(name = "role_id", nullable = false, updatable = false)
    private Integer roleId;

    @Column(name = "paradigm_id", updatable = false)
    private Integer paradigmId;

    @Column(updatable = false)
    private String stem1;

    @Column(updatable = false)
    private String stem2;

    @Column(updatable = false)
    private String stem3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> data;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> extras;

    @Column(updatable = false)
    private boolean hidden;

    @Column(name = "order_no", updatable = false)
    private Integer orderNo;
}
