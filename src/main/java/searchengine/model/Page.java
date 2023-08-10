package searchengine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "Page")
@Data
public class Page {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id")
    @NotNull
    private Site site;

    @OneToMany(mappedBy = "page")
    private List<Index> indexList;

    @Column(name = "path", columnDefinition = "TEXT NOT NULL, UNIQUE KEY pathIndex (path(256), site_id)")
    private String path;

    @Column(name = "code")
    @NotNull
    private int code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    @NotNull
    private String content;
}
