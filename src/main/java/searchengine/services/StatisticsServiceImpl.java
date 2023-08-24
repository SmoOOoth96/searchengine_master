package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class StatisticsServiceImpl implements StatisticsService {
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;

    public StatisticsServiceImpl(SiteRepository siteRepository, LemmaRepository lemmaRepository) {
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
    }

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites((int)siteRepository.count());
        total.setIndexing(WebCrawler.isIndexing());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = siteRepository.findAll();
        for(int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            String url = site.getUrl();
            String name = site.getName();
            String status = site.getStatus().name();
            long time = site.getDateTime().atZone(ZoneId.of("Asia/Karachi")).toInstant().toEpochMilli();
            String error = site.getLastError();
            int pages = (int)siteRepository.count();
            int lemmas = (int)lemmaRepository.count();

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

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();

        data.setTotal(total);
        data.setDetailed(detailed);

        response.setStatistics(data);
        response.setResult(true);

        return response;
    }
}
