package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.CrawlerConfig;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.IndexingSuccessResponse;
import searchengine.exceptions.IndexingStoppedException;
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
    public ResponseEntity<IndexingResponse> startIndexing() {
        IndexingResponse response = null;
        if(WebCrawler.isIndexing()){
            response = new IndexingErrorResponse("Индексация уже запущена", false);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }else{
            Thread thread = new Thread(){
                @Override
                public void run() {
                    IndexingServiceImpl.this.start();
                }
            };
            thread.start();
            response = new IndexingSuccessResponse(true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    @Override
    public ResponseEntity<IndexingResponse> stopIndexing() {
        IndexingResponse response = null;
        if(WebCrawler.isIndexing()){
            stop();
            response = new IndexingSuccessResponse(true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }else{
            response = new IndexingErrorResponse("Индексация не запущена", false);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<IndexingResponse> indexPage(String entryUrl) {
        String domainName = getFullDomainName(entryUrl);
        Site site = siteRepository.findOneByUrl(domainName);
        IndexingResponse response = null;
        if(site == null){
            response = new IndexingErrorResponse(
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле",
                    false);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        Thread thread = new Thread(){
            @Override
            public void run() {
                indexSinglePage(entryUrl);
            }
        };
        thread.start();
        response = new IndexingSuccessResponse(true);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private void start(){
        Site site = null;
        try {
            forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            List<Site> sites = sitesList.getSites();
            for (int i = 0; i < sites.size(); i++) {
                String siteUrl = sites.get(i).getUrl();
                if(siteRepository.existsByUrl(siteUrl)){
                    siteRepository.deleteByUrl(siteUrl);
                }
            }//удаляем все записи таблиц Site и Page с базы

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
                    throw new IndexingStoppedException();
                }
                site.setStatus(Status.INDEXED);
                siteRepository.save(site);
            }
        } catch (IndexingStoppedException e) {
            if (site != null) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
                forkJoinPool.shutdown();
            }
        }catch (Exception e){
            e.printStackTrace();
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
            Site foundSite = siteRepository.findOneByUrl(site.getUrl());
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
        Site site = siteRepository.findOneByUrl(domainName);
        String relUrl = getRelativeUrl(entryUrl);
        String content = document.outerHtml();

        if(String.valueOf(statusCode).charAt(0) == '4' || String.valueOf(statusCode).charAt(0) == '5'){
            return;
        }

        Page newPage = new Page();
        newPage.setCode(statusCode);
        newPage.setSite(site);
        newPage.setContent(content);
        newPage.setPath(relUrl);
        if (pageRepository.existsByPath(relUrl)) {
            Page existPage = pageRepository.findOneByPath(relUrl);
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
            newLemma.setFrequency(1);

            Index newIndex = new Index();
            newIndex.setPage(newPage);
            newIndex.setLemma(newLemma);
            newIndex.setRank(frequency);

            Lemma foundLemma = lemmaRepository.findOneByLemmaAndSite(lemma, site);
            if(foundLemma != null){
                int foundLemmaFrequency = foundLemma.getFrequency();
                foundLemma.setFrequency(foundLemmaFrequency + 1);
                newIndex.setLemma(foundLemma);
                lemmaRepository.save(foundLemma);
                indexRepository.save(newIndex);
            }else{
                lemmaRepository.save(newLemma);
                indexRepository.save(newIndex);
            }
        }
        site.setDateTime(LocalDateTime.now());
        siteRepository.save(site);
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
