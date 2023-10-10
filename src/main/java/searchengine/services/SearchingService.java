package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.searching.SearchResponse;

public interface SearchingService {
    ResponseEntity<SearchResponse> search(String query, String site, int offset, int limit);
}
