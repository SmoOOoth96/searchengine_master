package searchengine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "Lemma")
@Data
public class Lemma {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id")
    @NotNull
    private Site site;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL)
    private List<Index> indexList;

    @Column(name = "lemma", columnDefinition = "VARCHAR(255)")
    @NotNull
    private String lemma;

    @Column(name = "frequency")
    @NotNull
    private int frequency;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lemma lemma1 = (Lemma) o;
        return Objects.equals(lemma, lemma1.lemma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lemma);
    }
}
