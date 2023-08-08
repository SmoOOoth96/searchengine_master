package searchengine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Site")
@Data
public class Site {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "site")
    private List<Page> pageList;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "site")
    private List<Lemma> lemmaList;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    @NotNull
    private Status status;

    @Column(name = "status_time")
    @NotNull
    private LocalDateTime dateTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255)")
    @NotNull
    private String url;

    @Column(name = "name", columnDefinition = "VARCHAR(255)")
    @NotNull
    private String name;
}
