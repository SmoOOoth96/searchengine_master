package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.SearchErrorResponse;
import searchengine.dto.searching.SearchResponse;
import searchengine.dto.searching.SearchSuccessResponse;
import searchengine.dto.searching.SiteData;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Log4j2
public class SearchingServiceImpl implements SearchingService{
    private final LemmaFinder lemmaFinder;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    public SearchingServiceImpl(LemmaFinder lemmaFinder, LemmaRepository lemmaRepository, SiteRepository siteRepository, PageRepository pageRepository, IndexRepository indexRepository) {
        this.lemmaFinder = lemmaFinder;
        this.lemmaRepository = lemmaRepository;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public ResponseEntity<SearchResponse> search(String query, String site, int offset, int limit) {
        SearchResponse response = null;
        if(query == null || query.isBlank()){
            response = new SearchErrorResponse(false, "Задан пустой поисковый запрос");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }else{
            List<SiteData> list = startSearching(query, site, offset, limit);

            response = new SearchSuccessResponse(true, list.size(), list);

            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    private List<SiteData> startSearching(String query, String enteredDomainName, int offset, int limit) {
        List<SiteData> result = new ArrayList<>();
        List<Lemma> lemmaList = new ArrayList<>();

        try {
            List<String> lemmasFromQuery = lemmaFinder.getLemmas(query.toLowerCase().trim());

            for (String lemmaFromQuery : lemmasFromQuery) {
                if (enteredDomainName == null || enteredDomainName.isBlank()) {
                    removeMostFrequentLemmas(lemmaFromQuery, lemmaList, null);
                }else {
                    Site site = siteRepository.findByUrl(enteredDomainName);
                    removeMostFrequentLemmas(lemmaFromQuery, lemmaList, site);
                }
            }

            if(lemmaList.isEmpty()){
                return new ArrayList<>();
            }

            sortByFrequencyAsc(lemmaList);

            List<String> lemmas = new ArrayList<>();
            String strToCheck = "";

            for (Lemma lemma : lemmaList) {
                if(strToCheck.equals("")){
                    strToCheck = lemma.getLemma();
                    lemmas.add(lemma.getLemma());
                }else if(!strToCheck.equals(lemma.getLemma())) {
                    strToCheck = lemma.getLemma();
                    lemmas.add(lemma.getLemma());
                }
            }

            List<Page> pageListByFirstLemma = addPagesByFirstLemma(lemmaList);
            String firstStrLemma = lemmas.get(0);
            int maxRelevance = Integer.MIN_VALUE;
            boolean isFirstLemma;
            boolean isAdded = false;

            for (int i = 0; i < pageListByFirstLemma.size(); i++) {
                Page foundPage = pageListByFirstLemma.get(i);
                for (String lemma : lemmas) {
                    List<String> list = lemmaFinder.getLemmas(lemma);
                    isFirstLemma = list.contains(firstStrLemma);
                    if (isFirstLemma) {
                        continue;
                    }
                    isAdded = false;
                    List<Lemma> foundLemmas = lemmaRepository.findByLemma(lemma);
                    for (Lemma foundLemma : foundLemmas) {
                        List<Index> indexList = indexRepository.findByLemmaAndPage(foundLemma, foundPage);
                        if(indexList.isEmpty()) {
                            continue;
                        }
                        isAdded = true;
                    }
                    if(!isAdded){
                        pageListByFirstLemma.remove(foundPage);
                        i = -1;
                        break;
                    }
                }
            }

            if(pageListByFirstLemma.isEmpty()) {
                return new ArrayList<>();
            }

            Map<Page, Double> pagesWithRelevance = getPagesWithRelevance(pageListByFirstLemma, lemmaList, maxRelevance);

            List<Map.Entry<Page, Double>> sortedPagesByRelevanceDesc = getSortedPagesByRelevance(pagesWithRelevance);

            for (Map.Entry<Page, Double> entry : sortedPagesByRelevanceDesc) {
                Page page = entry.getKey();

                Site pageSite = page.getSite();
                String content = page.getContent();
                String siteUrl = pageSite.getUrl();

                String domainName = siteUrl.substring(0, siteUrl.length() - 1);
                String siteName = pageSite.getName();
                String path = page.getPath();
                String title = getTitle(content);
                String snippet = getSnippet(content, lemmasFromQuery);
                double relRelevance = entry.getValue();

                SiteData siteData = new SiteData();
                siteData.setSite(domainName);
                siteData.setSiteName(siteName);
                siteData.setUri(path);
                siteData.setTitle(title);
                siteData.setSnippet(snippet);
                siteData.setRelevance(relRelevance);

                result.add(siteData);
            }

            result = result.stream().skip(offset).limit(limit).collect(Collectors.toList());

        }catch (Exception e){
            log.error("error", e);
        }

        return result;
    }

    private String getSnippet(String content, List<String> queryLemmas) {
        String[] words = content
                .replaceAll("<[^>]*>", " ")
                .trim()
                .replaceAll("[^А-Яа-я]+", " ")
                .trim()
                .split(" +");
        int start = 0;
        int end = 0;

        for (int i = 0; i < words.length; i++) {
            String str = words[i];

            List<String> arr = null;

            if(str.isBlank()) {
                continue;
            }

            arr = lemmaFinder.getLemmas(str);

            if(arr == null || arr.isEmpty()){
                continue;
            }

            for (String word : arr) {
                if (!word.isBlank() && queryLemmas.contains(word.toLowerCase())) {
                    words[i] = words[i].replace(words[i], "<b>" + words[i] + "</b>");

                    if (i > 13) {
                        start = i - 14;

                        if (i + 15 < words.length) {
                            end = i + 15;
                        } else {
                            end = words.length - 1;
                        }
                    } else {
                        if (i > 6) {
                            end = i + 15;
                        } else {
                            end = i + i + 15;
                        }
                    }
                    break;
                }
            }
        }

        StringBuilder snippet = new StringBuilder();

        for (int i = start; i < end; i++) {

            if(queryLemmas.contains(words[i])) {
                words[i] = words[i].replace(words[i], "<b>" + words[i] + "</b>");
            }

            snippet.append(words[i]).append(" ");
        }
        return snippet.toString();
    }

    private String getTitle(String content){
        Pattern pattern = Pattern.compile("<title>(.+?)</title>");
        Matcher matcher = pattern.matcher(content);

        if(matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private List<Map.Entry<Page, Double>> getSortedPagesByRelevance(Map<Page, Double> pagesWithRelevance) {
        List<Map.Entry<Page, Double>> result = new ArrayList<>(pagesWithRelevance.entrySet());

        result.sort(Map.Entry.comparingByValue());

        Collections.reverse(result);

        return result;
    }

    private Map<Page, Double> getPagesWithRelevance(List<Page> pageList, List<Lemma> lemmaList, int maxRelevance) {
        Map<Page, Double> result = new HashMap<>();
        for (Page page :
                pageList) {
            int absRelevance = 0;

            for (Lemma lemma :
                    lemmaList) {
                List<Index> foundIndexList = indexRepository.findByLemmaAndPage(lemma, page);

                if (foundIndexList.isEmpty()) {
                    continue;
                }

                for (Index foundIndex : foundIndexList) {
                    absRelevance += foundIndex.getRank();
                }
            }

            double relRelevance = (double) absRelevance / maxRelevance;
            result.put(page, relRelevance);
        }

        return result;
    }

    private List<Page> addPagesByFirstLemma(List<Lemma> lemmaList) {
        String strToCheck = "";
        List<Page> pageList = new ArrayList<>();
        boolean hasSameLemma = false;
        for (Lemma foundLemma :
                lemmaList) {
            if (strToCheck.equals("")) {
                strToCheck = foundLemma.getLemma();
                hasSameLemma = true;
                List<Index> indexList = indexRepository.findByLemma(foundLemma);
                if(!indexList.isEmpty()) {
                    indexList.forEach(index -> pageList.add(index.getPage()));
                }
            }else if(strToCheck.equals(foundLemma.getLemma())){
                List<Index> indexList = indexRepository.findByLemma(foundLemma);
                if(!indexList.isEmpty()) {
                    indexList.forEach(index -> pageList.add(index.getPage()));
                }
                hasSameLemma = true;
            }else {
                List<String> firstLemmaList = lemmaFinder.getLemmas(strToCheck);
                List<String> secondLemmaList = lemmaFinder.getLemmas(foundLemma.getLemma());
                for (String lemmaFromFirstList : firstLemmaList) {
                    for (String lemmaFromSecondList : secondLemmaList) {
                        if (lemmaFromFirstList.equals(lemmaFromSecondList)) {
                            List<Index> indexList = indexRepository.findByLemma(foundLemma);
                            if (!indexList.isEmpty()) {
                                indexList.forEach(index -> pageList.add(index.getPage()));
                            }
                            hasSameLemma = true;
                            break;
                        } else {
                            hasSameLemma = false;
                        }
                    }
                    if (hasSameLemma) {
                        break;
                    }
                }
            }
        }
        return pageList;
    }

    private void removeMostFrequentLemmas(String queryLemma, List<Lemma> newLemmaList, Site site) {
        int sumLemmaFrequency = lemmaRepository.sumFrequencyWhereLemmaLike(queryLemma);
        long sumPageAmount = pageRepository.count();

        List<Lemma> lemmaList = null;
        if (site == null) {
            lemmaList = lemmaRepository.findByLemma(queryLemma);
        }else{
            lemmaList = lemmaRepository.findByLemmaAndSite(queryLemma, site);
        }
        if (sumLemmaFrequency * 100L / sumPageAmount < 80) {
            newLemmaList.addAll(lemmaList);
        }
    }

    private void sortByFrequencyAsc(List<Lemma> lemmaList) {
        lemmaList.sort(new Comparator<Lemma>() {
            @Override
            public int compare(Lemma o1, Lemma o2) {
                int firstSum = lemmaRepository.sumFrequencyWhereLemmaLike(o1.getLemma());
                int secondSum = lemmaRepository.sumFrequencyWhereLemmaLike(o2.getLemma());
                return firstSum - secondSum;
            }
        });
    }
}
