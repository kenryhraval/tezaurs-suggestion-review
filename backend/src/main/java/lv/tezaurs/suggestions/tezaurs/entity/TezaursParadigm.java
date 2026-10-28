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
@Table(name = "paradigms", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursParadigm {
    @Id
    private Integer id;

    @Column(name = "human_key", updatable = false)
    private String humanKey;

    @Column(updatable = false)
    private String caption;

    @Column(nullable = false, updatable = false)
    private Integer stems;
}
