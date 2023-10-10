package searchengine.services;

import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final CrawlerConfig crawlerConfig;
    private ForkJoinPool forkJoinPool;

    public IndexingServiceImpl(SitesList sites, SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository, CrawlerConfig crawlerConfig) {
        this.sites = sites;
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
        Site site = siteRepository.findByUrl(domainName);
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
            List<Site> siteList = this.sites.getSites();

            for (Site value : siteList) {
                String foundSiteUrl = value.getUrl();
                if (siteRepository.existsByUrl(foundSiteUrl)) {
                    siteRepository.deleteByUrl(foundSiteUrl);
                }
                long start = System.currentTimeMillis();

                Status status = Status.INDEXING;
                LocalDateTime time = LocalDateTime.now();
                String siteUrl = value.getUrl();
                String name = value.getName();

                site = new Site();
                site.setStatus(status);
                site.setDateTime(time);
                site.setUrl(siteUrl);
                site.setName(name);

                siteRepository.save(site);

                WebCrawler rootTask = new WebCrawler(siteUrl, pageRepository, siteRepository, lemmaRepository, indexRepository);
                WebCrawler.setUserAgent(crawlerConfig.getUserAgent());
                WebCrawler.setReferrer(crawlerConfig.getReferrer());
                forkJoinPool.invoke(rootTask);

                if (!WebCrawler.isIndexing()) {
                    throw new IndexingStoppedException();
                }

                site.setStatus(Status.INDEXED);
                siteRepository.save(site);

                long end = System.currentTimeMillis();
                long result = end - start;
                log.error("hours: " + result / 1000 / 60 / 60);
                log.error("minutes: " + result / 1000 / 60);
                log.error("seconds: " + result / 1000);
            }
        } catch (IndexingStoppedException e) {
            log.error("error", e);
            if (site != null) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
                forkJoinPool.shutdown();
            }
        }catch (Exception e){
            log.error("error", e);
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
            }
        }
    }

    private void stop(){
        forkJoinPool.shutdownNow();
        WebCrawler.setIndexing(false);
        List<Site> siteList = sites.getSites();

        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            log.error("error", e);
        }

        for (Site site : siteList) {
            Site foundSite = siteRepository.findByUrl(site.getUrl());
            if (foundSite != null && foundSite.getStatus() != Status.INDEXED) {
                setValues(foundSite);
            } else {
                setValues(site);
            }
        }
    }

    private void indexSinglePage(String entryUrl){
        Document document = null;
        LemmaFinder lemmaFinder = null;
        try {
            lemmaFinder = new LemmaFinder();
            Thread.sleep(125);
            document = Jsoup.connect(entryUrl)
                    .userAgent(crawlerConfig.getUserAgent())
                    .referrer(crawlerConfig.getReferrer())
                    .timeout(10000)
                    .get();
        } catch (InterruptedException | IOException e) {
            log.error("error", e);
        }

        try {
            if(document == null){
                return;
            }
            int statusCode = document.connection().response().statusCode();
            String domainName = getFullDomainName(entryUrl);
            Site site = siteRepository.findByUrl(domainName);
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

            Page foundPage = pageRepository.findByPath(relUrl);

            if (foundPage != null) {
                updateExistingPage(foundPage, statusCode, site, content, relUrl);
                newPage = foundPage;
            }else{
                pageRepository.save(newPage);
            }

            Map<String, Integer> lemmaList = lemmaFinder.getLemmasAndFrequency(content);
            for (Map.Entry<String, Integer> s :
                    lemmaList.entrySet()) {
                String lemma = s.getKey();
                int rank = s.getValue();

                Lemma newLemma = new Lemma();
                newLemma.setSite(site);
                newLemma.setLemma(lemma);
                newLemma.setFrequency(1);

                Index newIndex = new Index();
                newIndex.setPage(newPage);
                newIndex.setLemma(newLemma);
                newIndex.setRank(rank);

                List<Lemma> foundLemmaList = lemmaRepository.findByLemmaAndSite(lemma, site);

                if (!foundLemmaList.isEmpty()) {
                    updateExistingLemmas(foundLemmaList, newIndex);
                } else {
                    lemmaRepository.save(newLemma);
                    indexRepository.save(newIndex);
                }
            }
            site.setDateTime(LocalDateTime.now());
            siteRepository.save(site);
        } catch (Exception e) {
            log.error("error", e);
        }
    }

    private void setValues(Site site) {
        site.setStatus(Status.FAILED);
        site.setDateTime(LocalDateTime.now());
        site.setUrl(site.getUrl());
        site.setName(site.getName());
        site.setLastError("Индексация остановлена пользователем");
        siteRepository.save(site);
    }

    private void updateExistingLemmas(List<Lemma> lemmaList, Index newIndex) {
        for (Lemma foundLemma : lemmaList) {
            int foundLemmaFrequency = foundLemma.getFrequency();
            foundLemma.setFrequency(foundLemmaFrequency + 1);

            newIndex.setLemma(foundLemma);

            lemmaRepository.save(foundLemma);
            indexRepository.save(newIndex);
        }
    }

    private void updateExistingPage(Page foundPage, int statusCode, Site site, String content, String relUrl) {
        foundPage.setCode(statusCode);
        foundPage.setSite(site);
        foundPage.setContent(content);
        foundPage.setPath(relUrl);
        pageRepository.save(foundPage);
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
