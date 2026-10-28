package lv.tezaurs.suggestions.tezaurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "entries", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursEntry {
    @Id
    private Integer id;

    @Column(name = "type_id", nullable = false, updatable = false)
    private Integer typeId;

    @Column(name = "homonym_no", nullable = false, updatable = false)
    private Integer homonymNo;

    @Column(nullable = false, updatable = false)
    private String heading;

    @Column(name = "human_key", nullable = false, updatable = false, insertable = false)
    private String humanKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> data;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    @Column(updatable = false)
    private boolean hidden;

    @OneToMany(mappedBy = "entry")
    @OrderBy("orderNo ASC, id ASC")
    private List<TezaursLexeme> lexemes = new ArrayList<>();
}
