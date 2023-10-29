package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    boolean existsByPathAndSite(String path, Site site);
    Page findByPathAndSite(String path, Site site);
    int countBySite(Site site);
}
