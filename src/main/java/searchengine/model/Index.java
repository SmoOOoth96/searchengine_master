package searchengine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Table(name = "`Index`")
@Data
public class Index {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "page_id")
    @NotNull
    private Page page;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "lemma_id")
    @NotNull
    private Lemma lemma;

    @Column(name = "`rank`")
    @NotNull
    private float rank;
}
