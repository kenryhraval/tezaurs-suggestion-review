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
@Table(name = "grammar_flag_values", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursGrammarFlagValue {
    @Id
    private Integer id;

    @Column(name = "flag_id", nullable = false, updatable = false)
    private Integer flagId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flag_id", insertable = false, updatable = false)
    private TezaursGrammarFlag flag;

    @Column(nullable = false, updatable = false)
    private String value;

    @Column(name = "is_deprecated", nullable = false, updatable = false)
    private boolean deprecated;

    @Column(name = "order_no", updatable = false)
    private Integer orderNo;
}
