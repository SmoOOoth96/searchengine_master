package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.indexing.IndexingErrorResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.exceptions.IndexingRunningException;
import searchengine.exceptions.IndexingStartedException;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingErrorResponse> startIndexing(){
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @ExceptionHandler
    private ResponseEntity<IndexingErrorResponse> handleException(IndexingRunningException e){
        IndexingErrorResponse response = new IndexingErrorResponse(false, "Что-то пошло не так");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler
    private ResponseEntity<IndexingErrorResponse> handleException(IndexingStartedException e){
        IndexingErrorResponse response = new IndexingErrorResponse(false, "Индексация уже запущена");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
