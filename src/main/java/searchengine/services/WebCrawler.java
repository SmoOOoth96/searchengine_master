package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class WebCrawler extends RecursiveAction {
    private final String url;
    private final Site site;
    private static PageRepository pageRepository;
    private static SiteRepository siteRepository;

    public WebCrawler(String url, PageRepository pageRepository, SiteRepository siteRepository) {
        this.url = url;
        WebCrawler.pageRepository = pageRepository;
        WebCrawler.siteRepository = siteRepository;
        this.site = siteRepository.findByUrl(getFullDomainName(url));
    }

    public WebCrawler(String url) {
        this.url = url;
        this.site = siteRepository.findByUrl(getFullDomainName(url));
    }

    @Override
    public void compute() {
        List<WebCrawler> appList = new ArrayList<>();
        try {
            Thread.sleep(150);
            Document document = Jsoup.connect(url).ignoreHttpErrors(true).get();
            Elements elements = document.select("a");
            for (Element s :
                    elements) {
                String entryUrl = s.attr("href");
                int statusCode = document.connection().response().statusCode();

                String newUrl = makeAbsUrl(entryUrl);

                if(isUrlValid(newUrl, entryUrl)){
                    WebCrawler app = new WebCrawler(newUrl);
                    Page newPage = new Page();
                    newPage.setCode(statusCode);
                    newPage.setSite(this.site);
                    newPage.setContent(document.html());
                    newPage.setPath(entryUrl);
                    pageRepository.save(newPage);
                    app.fork();
                    appList.add(app);
                }
            }
        } catch (Exception e){
            System.err.println(e);
        }
        appList.forEach(WebCrawler::join);
    }

    //из относительной ссылки => guides/gs/circuit-breaker/ в абсолютную => https://spring.io/guides/gs/circuit-breaker/
    private String makeAbsUrl(String url) {
        if(url.startsWith("/")) {
            return getFullDomainName(this.url) + url.substring(1);
        }
        return url;
    }

    //получить из https://lenta.ru/news/2023/08/07/uuuar/ полное доменное имя https://lenta.ru/
    private String getFullDomainName(String url) {
        Pattern fullDomainName = Pattern.compile("https://(www)?\\.\\w+\\.(com|ru|org|net|de)/");
        Matcher matcher = fullDomainName.matcher(url);

        return matcher.find() ? matcher.group() : url;
    }

    //проверка на валидность ссылки
    private boolean isUrlValid(String newUrl, String entryUrl) {
        return !newUrl.endsWith("pdf")
                && !newUrl.endsWith("jpg")
                && !newUrl.endsWith("jpeg")
                && !newUrl.endsWith("png")
                && !newUrl.endsWith("gif")
                && !newUrl.endsWith("zip")
                && !newUrl.endsWith("bmp")
                && !newUrl.endsWith("exe")
                && newUrl.startsWith(this.site.getUrl())
                && !pageRepository.existsByPath(entryUrl);
    }
}
