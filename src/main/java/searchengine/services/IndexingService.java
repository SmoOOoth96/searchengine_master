package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Page;
import searchengine.model.Site;

public interface IndexingService {
    IndexingResponse startIndexing();

    IndexingResponse stopIndexing();

    IndexingResponse indexPage(String url);

    void save(Site site);

    void update(int id, Site updatedSite);

    void save(Page page);

    void update(int id, Page updatedPage);
}
