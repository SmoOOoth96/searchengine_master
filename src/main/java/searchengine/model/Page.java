package searchengine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "Page", indexes = @jakarta.persistence.Index(columnList = "path, site_id", unique = true, name = "pathIndex"))
@Data
public class Page {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id")
    @NotNull
    private Site site;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "page")
    private List<Index> indexList;

    @Column(name = "path", columnDefinition = "TEXT NOT NULL")
    private String path;

    @Column(name = "code")
    private int code;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
}
