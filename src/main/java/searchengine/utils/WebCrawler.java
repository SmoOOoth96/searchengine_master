package searchengine.utils;

import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class WebCrawler extends RecursiveAction {
    private final String url;
    private final Site site;
    private static PageRepository pageRepository;
    private static SiteRepository siteRepository;
    private static LemmaRepository lemmaRepository;
    private static IndexRepository indexRepository;
    private static String userAgent;
    private static String referrer;
    private static volatile boolean indexing;

    public WebCrawler(String url, PageRepository pageRepository, SiteRepository siteRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.url = url;
        WebCrawler.pageRepository = pageRepository;
        WebCrawler.siteRepository = siteRepository;
        WebCrawler.lemmaRepository = lemmaRepository;
        WebCrawler.indexRepository = indexRepository;
        this.site = WebCrawler.siteRepository.findByUrl(getFullDomainName(url));
        indexing = true;
    }

    public WebCrawler(String url) {
        this.url = url;
        this.site = siteRepository.findByUrl(getFullDomainName(url));
    }

    @Override
    public void compute() {
        List<WebCrawler> taskList = new ArrayList<>();
        try {
            Thread.sleep(150);
            Document document = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(10000)
                    .get();

            int statusCode = document.connection().response().statusCode();
            String relUrl = getRelativeUrl(url);
            String content = document.outerHtml();
            String bodyText = document.body().text();

            Page newPage = initNewPage(statusCode, this.site, content, relUrl);

            boolean pageExists = pageRepository.existsByPathAndSite(relUrl, this.site);

            if(!pageExists && !hasErrorStatus(document)) {
                pageRepository.save(newPage);

                initNewLemmaAndIndex(this.site, newPage, bodyText);

                taskList = findValidUrlsIn(document);
            }

            taskList.forEach(WebCrawler::join);
        }catch (Exception e){
            log.error("error", e);
        }
    }

    private List<WebCrawler> findValidUrlsIn(Document document) {
        List<WebCrawler> taskList = new ArrayList<>();

        Elements elements = document.select("a[href]");

        for (Element value :
                elements) {
            String entryUrl = value.attr("href");

            String entryAbsUrl = getAbsUrl(entryUrl);
            String entryRelUrl = getRelativeUrl(entryUrl);

            if(!indexing){
                taskList.clear();
                break;
            }

            if (isUrlValid(entryAbsUrl, entryRelUrl)) {
                addTask(entryAbsUrl, taskList);
            }
        }
        return taskList;
    }

    private void addTask(String entryAbsUrl, List<WebCrawler> taskList) {
        WebCrawler task = new WebCrawler(entryAbsUrl);
        taskList.add(task);
        task.fork();
        this.site.setDateTime(LocalDateTime.now());
        siteRepository.save(this.site);
    }

    private void initNewLemmaAndIndex(Site site, Page newPage, String content) throws IOException {
        LemmaFinder lemmaFinder = new LemmaFinder();
        Map<String, Integer> lemmaList = lemmaFinder.getLemmasAndFrequency(content);
        for (Map.Entry<String, Integer> value :
                lemmaList.entrySet()) {
            String lemmaWord = value.getKey();
            int rank = value.getValue();

            Lemma newLemma = new Lemma();
            newLemma.setSite(site);
            newLemma.setLemma(lemmaWord);
            newLemma.setFrequency(1);

            Index newIndex = new Index();
            newIndex.setPage(newPage);
            newIndex.setLemma(newLemma);
            newIndex.setRank(rank);

            List<Lemma> foundLemmaList = lemmaRepository.findByLemmaAndSite(lemmaWord, site);

            if(foundLemmaList.isEmpty()){
                lemmaRepository.save(newLemma);
                indexRepository.save(newIndex);
            }else{
                updateExistingLemmas(foundLemmaList, newIndex);
            }
        }
    }

    private void updateExistingLemmas(List<Lemma> foundLemmaList, Index newIndex) {
        for (Lemma foundLemma : foundLemmaList) {
            foundLemma.setFrequency(foundLemma.getFrequency() + 1);

            newIndex.setLemma(foundLemma);

            lemmaRepository.save(foundLemma);
            indexRepository.save(newIndex);
        }
    }

    private boolean hasErrorStatus(Document document) {
        int statusCode = document.connection().response().statusCode();
        boolean hasClientError = String.valueOf(statusCode).charAt(0) == '4';
        boolean hasServerError = String.valueOf(statusCode).charAt(0) == '5';

        return hasClientError || hasServerError;
    }

    private Page initNewPage(int statusCode, Site site, String content, String relUrl) {
        Page newPage = new Page();
        newPage.setCode(statusCode);
        newPage.setSite(site);
        newPage.setContent(content);
        newPage.setPath(relUrl);
        return newPage;
    }

    public String getRelativeUrl(String url) {
        if (!url.startsWith("/")) {
            String domainName = getFullDomainName(url);
            if (domainName.startsWith(this.site.getUrl())) {
                return url.substring(domainName.length() - 1);
            }
        }
        return url;
    }

    private String getAbsUrl(String url) {
        if(url.startsWith("/")) {
            return getFullDomainName(this.url) + url.substring(1);
        }
        return url;
    }

    //получить из https://lenta.ru/news/2023/08/07/uuuar/ полное доменное имя https://lenta.ru/
    public String getFullDomainName(String url) {
        Pattern fullDomainName = Pattern.compile("https://(www\\.)?\\w+\\.[a-z]+/");
        Matcher matcher = fullDomainName.matcher(url);

        return matcher.find() ? matcher.group() : url;
    }

    //проверка на валидность ссылки
    private boolean isUrlValid(String absUrl, String relUrl) {
        return !absUrl.endsWith("pdf")
                && !absUrl.endsWith("jpg")
                && !absUrl.endsWith("jpeg")
                && !absUrl.endsWith("png")
                && !absUrl.endsWith("gif")
                && !absUrl.endsWith("zip")
                && !absUrl.endsWith("bmp")
                && !absUrl.endsWith("exe")
                && absUrl.startsWith(this.site.getUrl())
                && !absUrl.equals("")
                && relUrl.startsWith("/")
                && !pageRepository.existsByPathAndSite(relUrl, this.site);
    }

    public static void setUserAgent(String userAgent) {
        WebCrawler.userAgent = userAgent;
    }

    public static void setReferrer(String referrer) {
        WebCrawler.referrer = referrer;
    }

    public static boolean isIndexing() {
        return indexing;
    }

    public static void setIndexing(boolean indexing) {
        WebCrawler.indexing = indexing;
    }
}
