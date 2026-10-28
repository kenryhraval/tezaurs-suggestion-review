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
@Table(name = "entry_types", schema = "dict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TezaursEntryType {
    @Id
    private Integer id;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(updatable = false)
    private String description;
}
