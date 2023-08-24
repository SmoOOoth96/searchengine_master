package searchengine.services;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            LemmaFinder lemmaFinder = new LemmaFinder();
            Thread.sleep(500);
            Document document = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(10000)
                    .get();

            Elements elements = document.select("a[href]");

            int statusCode = document.connection().response().statusCode();
            String relUrl = getRelativeUrl(url);
            String content = document.outerHtml();

            Page newPage = new Page();
            newPage.setCode(statusCode);
            newPage.setSite(this.site);
            newPage.setContent(content);
            newPage.setPath(relUrl);

            if(!pageRepository.existsByPath(relUrl)
                    && String.valueOf(statusCode).charAt(0) != '4'
                    && String.valueOf(statusCode).charAt(0) != '5') {
                pageRepository.save(newPage);

                Map<String, Integer> lemmaList = lemmaFinder.getAllLemmas(content);
                for (Map.Entry<String, Integer> s :
                        lemmaList.entrySet()) {
                    String lemmaWord = s.getKey();
                    int rank = s.getValue();

                    Lemma newLemma = new Lemma();
                    newLemma.setSite(this.site);
                    newLemma.setLemma(lemmaWord);
                    newLemma.setFrequency(1);

                    Index newIndex = new Index();
                    newIndex.setPage(newPage);
                    newIndex.setLemma(newLemma);
                    newIndex.setRank(rank);
                    List<Lemma> foundLemmaList = lemmaRepository.findByLemmaAndSite(lemmaWord, this.site);
                    if(!foundLemmaList.isEmpty()){
                        int foundFrequencySum = 0;
                        for (Lemma foundLemma : foundLemmaList) {
                            foundFrequencySum += foundLemma.getFrequency();
                            lemmaRepository.delete(foundLemma);
                        }
                        newLemma.setFrequency(foundFrequencySum + 1);
                        lemmaRepository.save(newLemma);
                    }else {
                        lemmaRepository.save(newLemma);
                    }
                    indexRepository.save(newIndex);
                }

                for (Element s :
                        elements) {
                    String entryUrl = s.attr("href");

                    String entryAbsUrl = getAbsUrl(entryUrl);
                    String entryRelUrl = getRelativeUrl(entryUrl);

                    if (isUrlValid(entryAbsUrl, entryRelUrl)) {
                        if(!indexing){
                            taskList.clear();
                            break;
                        }
                        WebCrawler task = new WebCrawler(entryAbsUrl);
                        taskList.add(task);
                        task.fork();
                        this.site.setDateTime(LocalDateTime.now());
                        siteRepository.save(this.site);
                    }
                }
            }
            taskList.forEach(WebCrawler::join);
        }catch (Exception e){
            e.printStackTrace();
        }
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
                && !pageRepository.existsByPath(relUrl);
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
