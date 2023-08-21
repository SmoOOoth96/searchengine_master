package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.config.CrawlerConfig;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.IndexingSuccessResponse;
import searchengine.exceptions.IndexingRunningException;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final CrawlerConfig crawlerConfig;
    private ForkJoinPool forkJoinPool;

    public IndexingServiceImpl(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository, CrawlerConfig crawlerConfig) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.crawlerConfig = crawlerConfig;
    }

    @Override
    public IndexingResponse startIndexing() {
        if(WebCrawler.isIndexing()){
            return new IndexingErrorResponse("Индексация уже запущена", false);
        }else{
            Thread thread = new Thread(){
                @Override
                public void run() {
                    IndexingServiceImpl.this.start();
                }
            };
            thread.start();
            return new IndexingSuccessResponse(true);
        }
    }

    @Override
    public IndexingResponse stopIndexing() {
        if(WebCrawler.isIndexing()){
            stop();
            return new IndexingSuccessResponse(true);
        }else{
            return new IndexingErrorResponse("Индексация не запущена", false);
        }
    }

    @Override
    public IndexingResponse indexPage(String entryUrl) {
        String domainName = getFullDomainName(entryUrl);
        Site site = siteRepository.findByUrl(domainName);
        if(site == null){
            return new IndexingErrorResponse(
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле",
                    false);
        }
        Thread thread = new Thread(){
            @Override
            public void run() {
                indexSinglePage(entryUrl);
            }
        };
        thread.start();
        return new IndexingSuccessResponse(true);
    }

    private void start(){
        Site site = null;
        try {
            forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            List<Site> sites = sitesList.getSites();
            sites.forEach(s -> siteRepository.deleteAllByUrl(s.getUrl()));//удаляем все записи таблиц Site и Page с базы

            for (int i = 0; i < sites.size(); i++) {
                site = new Site();
                site.setStatus(Status.INDEXING);
                site.setDateTime(LocalDateTime.now());
                site.setUrl(sites.get(i).getUrl());
                site.setName(sites.get(i).getName());
                siteRepository.save(site);
                WebCrawler rootTask = new WebCrawler(sites.get(i).getUrl(), pageRepository, siteRepository, lemmaRepository, indexRepository);
                WebCrawler.setUserAgent(crawlerConfig.getUserAgent());
                WebCrawler.setReferrer(crawlerConfig.getReferrer());
                forkJoinPool.invoke(rootTask);
                if (!WebCrawler.isIndexing()) {
                    throw new IndexingRunningException();
                }
                site.setStatus(Status.INDEXED);
                siteRepository.save(site);
            }
        } catch (IndexingRunningException e) {
            if (site != null) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
                forkJoinPool.shutdown();
            }
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
            }
        }
    }

    private void stop(){
        forkJoinPool.shutdownNow();
        WebCrawler.setIndexing(false);
        List<Site> allSites = sitesList.getSites();
        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < allSites.size(); i++) {
            Site site = allSites.get(i);
            Site foundSite = siteRepository.findByUrl(site.getUrl());
            if (foundSite != null && foundSite.getStatus() != Status.INDEXED && foundSite.getStatus() != Status.FAILED) {
                foundSite.setStatus(Status.FAILED);
                foundSite.setDateTime(LocalDateTime.now());
                foundSite.setUrl(site.getUrl());
                foundSite.setName(site.getName());
                foundSite.setLastError("Индексация остановлена пользователем");
                siteRepository.save(foundSite);
            } else {
                site.setStatus(Status.FAILED);
                site.setDateTime(LocalDateTime.now());
                site.setUrl(site.getUrl());
                site.setName(site.getName());
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
            }
        }
    }

    private void indexSinglePage(String entryUrl){
        Document document = null;
        LemmaFinder lemmaFinder = null;
        try {
            lemmaFinder = new LemmaFinder();
            Thread.sleep(500);
            document = Jsoup.connect(entryUrl)
                    .userAgent(crawlerConfig.getUserAgent())
                    .referrer(crawlerConfig.getReferrer())
                    .timeout(10000)
                    .get();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        int statusCode = document.connection().response().statusCode();
        String domainName = getFullDomainName(entryUrl);
        Site site = siteRepository.findByUrl(domainName);
        String relUrl = getRelativeUrl(entryUrl);
        String content = document.outerHtml();

        Page newPage = new Page();
        newPage.setCode(statusCode);
        newPage.setSite(site);
        newPage.setContent(content);
        newPage.setPath(relUrl);
        if (pageRepository.existsByPath(relUrl)) {
            Page existPage = pageRepository.findByPath(relUrl);
            pageRepository.delete(existPage);
            pageRepository.save(newPage);
        }else{
            pageRepository.save(newPage);
        }
        Map<String, Integer> lemmaList = lemmaFinder.getAllLemmas(content);
        for (Map.Entry<String, Integer> s :
                lemmaList.entrySet()) {
            Lemma newLemma = new Lemma();
            String lemma = s.getKey();
            int frequency = s.getValue();
            newLemma.setSite(site);
            newLemma.setLemma(lemma);
            newLemma.setFrequency(frequency);

            Index newIndex = new Index();
            newIndex.setPage(newPage);
            newIndex.setLemma(newLemma);
            newIndex.setRank(frequency);
            lemmaRepository.save(newLemma);
            indexRepository.save(newIndex);
        }
        site.setDateTime(LocalDateTime.now());
        siteRepository.save(site);
    }


    @Transactional
    @Override
    public void save(Site site) {
        siteRepository.save(site);
    }

    @Transactional
    @Override
    public void update(int id, Site updatedSite) {
        updatedSite.setId(id);
        siteRepository.save(updatedSite);
    }

    @Transactional
    @Override
    public void save(Page page) {
        pageRepository.save(page);
    }

    @Transactional
    @Override
    public void update(int id, Page updatedPage) {
        updatedPage.setId(id);
        pageRepository.save(updatedPage);
    }

    private String getRelativeUrl(String url) {
        if (!url.startsWith("/")) {
            String domainName = getFullDomainName(url);
            return url.substring(domainName.length() - 1);
        }
        return url;
    }

    private String getFullDomainName(String url) {
        Pattern fullDomainName = Pattern.compile("https://(www\\.)?\\w+\\.[a-z]+/");
        Matcher matcher = fullDomainName.matcher(url);

        return matcher.find() ? matcher.group() : url;
    }
}
