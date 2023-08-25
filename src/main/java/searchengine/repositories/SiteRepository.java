package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    @Transactional
    void deleteByUrl(String url);
    Site findOneByUrl(String newUrl);
    boolean existsByUrl(String url);
}
