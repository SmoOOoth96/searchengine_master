package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class WebCrawler extends RecursiveAction {
    private final String url;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final WebCrawler parent;



    public WebCrawler(String url, WebCrawler parent) {
        this.url = url;
        this.parent = parent;
        this.pageRepository = parent.pageRepository;
        this.siteRepository = parent.siteRepository;
    }

    public WebCrawler(String url, PageRepository pageRepository, SiteRepository siteRepository, WebCrawler parent) {
        this.url = url;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.parent = parent;
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
                String newUrl = s.attr("href");

                newUrl = makeAbsUrl(newUrl);

                if(isUrlValid(newUrl)){
                    pag.add(newUrl);
                    WebCrawler app = new WebCrawler(newUrl, this);
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

    //получить из https://lenta.ru/news/2023/08/07/uuuar/ доменное имя => lenta.ru
    private String getDomainName(String url) {
        Pattern domainName = Pattern.compile("\\w+\\.(com|ru|org|net|de)");
        Matcher matcher = domainName.matcher(url);

        return matcher.find() ? matcher.group() : url;
    }


    //получить из https://lenta.ru/news/2023/08/07/uuuar/ полное доменное имя https://lenta.ru/
    private String getFullDomainName(String url) {
        Pattern fullDomainName = Pattern.compile("https://(www)?\\.\\w+\\.(com|ru|org|net|de)/");
        Matcher matcher = fullDomainName.matcher(url);

        return matcher.find() ? matcher.group() : url;
    }

    //проверка на валидность ссылки
    private boolean isUrlValid(String newUrl) {
        if (!newUrl.equals(url)
                && !newUrl.endsWith("pdf")
                && !newUrl.endsWith("jpg")
                && !newUrl.endsWith("jpeg")
                && !newUrl.endsWith("png")
                && !newUrl.endsWith("gif")
                && !newUrl.endsWith("zip")
                && !newUrl.endsWith("bmp")
                && !newUrl.endsWith("exe")
                && newUrl.contains(getFullDomainName(newUrl))
                && !.contains(newUrl))
        {
            if(newUrl.endsWith(".text")){
                System.out.println("TEXT FILE");
            }
            if(newUrl.contains(getDomainName(newUrl)) && !newUrl.contains(getFullDomainName(newUrl)) && !newUrl.endsWith(".html")){
                System.out.println("ДРУГИЕ ССЫЛКИ");
            }
            return true;
        }
        return false;
    }
}
