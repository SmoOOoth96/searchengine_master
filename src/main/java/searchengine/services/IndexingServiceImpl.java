package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.exceptions.IndexingRunningException;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class IndexingServiceImpl implements IndexingService{
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public IndexingServiceImpl(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    @Override
    public IndexingErrorResponse startIndexing() {
        Site site = null;
        IndexingErrorResponse response = null;
        try {
            List<Site> sites = sitesList.getSites();
            response = new IndexingErrorResponse();
            response.setResult(true);
            for (int i = 0; i < sites.size(); i++) {
                site = new Site();
                site.setStatus(Status.INDEXING);
                site.setDateTime(LocalDateTime.now());
                site.setUrl(sites.get(i).getUrl());
                site.setName(sites.get(i).getName());
                siteRepository.deleteAllByName(sites.get(i).getName());//удаляем все записи таблиц Site и Page с базы
                siteRepository.save(site);
                recursiveFunc(sites.get(i).getUrl(), site);
                site.setStatus(Status.INDEXED);
            }
        }catch (IndexingRunningException e){
            if (site != null) {
                site.setStatus(Status.FAILED);
                site.setLastError(response.getError());
            }
        }
        return response;
    }

    @Transactional
    @Override
    public void save(Site site){
        siteRepository.save(site);
    }

    @Transactional
    @Override
    public void update(int id, Site updatedSite){
        updatedSite.setId(id);
        siteRepository.save(updatedSite);
    }

    @Transactional
    @Override
    public void save(Page page){
        pageRepository.save(page);
    }

    @Transactional
    @Override
    public void update(int id, Page updatedPage){
        updatedPage.setId(id);
        pageRepository.save(updatedPage);
    }
}
