package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    List<Lemma> findByLemmaAndSite(String lemmaWord, Site site);

    List<Lemma> findByLemma(String queryLemma);

    @Query(value = "SELECT sum(frequency) FROM search_engine.lemma where lemma = ?1", nativeQuery = true)
    Integer sumFrequencyWhereLemmaLike(String lemma);

    int countBySite(Site site);
}
