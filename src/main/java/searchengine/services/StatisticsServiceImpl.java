package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class StatisticsServiceImpl implements StatisticsService {
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;

    public StatisticsServiceImpl(SiteRepository siteRepository, LemmaRepository lemmaRepository, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
    }

    @Override
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = null;
        try {
            TotalStatistics total = new TotalStatistics();
            total.setSites((int)siteRepository.count());
            total.setIndexing(WebCrawler.isIndexing());

            List<DetailedStatisticsItem> detailed = new ArrayList<>();
            List<Site> sitesList = siteRepository.findAll();
            for (Site site : sitesList) {
                String url = site.getUrl();
                String name = site.getName();
                String status = site.getStatus().name();
                long time = site.getDateTime().atZone(ZoneId.of("Asia/Karachi")).toInstant().toEpochMilli();
                String error = site.getLastError();
                int pages = pageRepository.countBySite(site);
                int lemmas = lemmaRepository.countBySite(site);

                DetailedStatisticsItem item = new DetailedStatisticsItem();
                item.setUrl(url);
                item.setName(name);
                item.setStatus(status);
                item.setStatusTime(time);
                item.setError(error);
                item.setPages(pages);
                item.setLemmas(lemmas);

                total.setPages(total.getPages() + pages);
                total.setLemmas(total.getLemmas() + lemmas);

                detailed.add(item);
            }

            response = new StatisticsResponse();
            StatisticsData data = new StatisticsData();

            data.setTotal(total);
            data.setDetailed(detailed);

            response.setStatistics(data);
            response.setResult(true);

            return response;
        } catch (Exception e) {
            log.error("error", e);
        }
        return response;
    }
}
