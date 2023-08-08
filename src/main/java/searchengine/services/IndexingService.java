package searchengine.services;

import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.model.Page;
import searchengine.model.Site;

public interface IndexingService {
    IndexingErrorResponse startIndexing();

    void save(Site site);

    void update(int id, Site updatedSite);

    void save(Page page);

    void update(int id, Page updatedPage);
}
