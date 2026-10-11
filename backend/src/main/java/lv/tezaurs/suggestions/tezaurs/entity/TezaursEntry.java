package lv.tezaurs.suggestions.tezaurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Immutable
@Table(name = "entries", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursEntry {
    @Id
    private Integer id;

    @Column(nullable = false, updatable = false)
    private String heading;

    @Column(name = "human_key", nullable = false, updatable = false, insertable = false)
    private String humanKey;

    @OneToMany(mappedBy = "entry")
    @OrderBy("orderNo ASC, id ASC")
    private List<TezaursLexeme> lexemes = new ArrayList<>();
}
