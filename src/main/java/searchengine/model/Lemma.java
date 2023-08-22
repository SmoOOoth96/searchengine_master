package searchengine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

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

}
