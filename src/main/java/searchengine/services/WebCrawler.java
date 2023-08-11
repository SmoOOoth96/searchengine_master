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
        this.site = WebCrawler.siteRepository.findByUrl(getFullDomainName(url));
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

                String entryAbsUrl = getAbsUrl(entryUrl);
                String relUrl = getRelativeUrl(entryUrl);

                if(isUrlValid(entryAbsUrl, relUrl)){
                    WebCrawler app = new WebCrawler(entryAbsUrl);
                    Page newPage = new Page();
                    newPage.setCode(statusCode);
                    newPage.setSite(this.site);
                    newPage.setContent(s.ownerDocument().html());
                    newPage.setPath(relUrl);
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

    private String getRelativeUrl(String url) {
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
    private String getFullDomainName(String url) {
        Pattern fullDomainName = Pattern.compile("https://(www\\.)?\\w+\\.(com|ru|org|net|de)/");
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
                && relUrl.startsWith("/")
                && !pageRepository.existsByPath(relUrl);
    }
}
