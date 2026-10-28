package lv.tezaurs.suggestions.tezaurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "grammar_flags", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursGrammarFlag {
    @Id
    private Integer id;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(updatable = false)
    private String description;

    @Column(updatable = false)
    private String caption;

    @Column(name = "is_deprecated", nullable = false, updatable = false)
    private boolean deprecated;

    @Column(name = "is_multiple", nullable = false, updatable = false)
    private boolean multiple;

    @Column(name = "permitted_values", nullable = false, updatable = false)
    private String permittedValues;

    @Column(name = "order_no", updatable = false)
    private Integer orderNo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", updatable = false)
    private String[] scope;

    @OneToMany(mappedBy = "flag", fetch = FetchType.LAZY)
    @OrderBy("orderNo ASC, value ASC")
    private List<TezaursGrammarFlagValue> values = new ArrayList<>();
}
