package searchengine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Objects;

@Entity
@Table(name = "`Index`")
@Data
public class Index {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id")
    @NotNull
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id")
    @NotNull
    private Lemma lemma;

    @Column(name = "`rank`")
    @NotNull
    private int rank;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return Objects.equals(page, index.page) && Objects.equals(lemma, index.lemma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(page, lemma);
    }
}
