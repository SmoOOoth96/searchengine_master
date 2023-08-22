package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {
    ResponseEntity<IndexingResponse> startIndexing();

    ResponseEntity<IndexingResponse> stopIndexing();

    ResponseEntity<IndexingResponse> indexPage(String url);
}
