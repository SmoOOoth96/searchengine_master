package searchengine.dto.searching;

import lombok.Getter;

@Getter
public class SearchErrorResponse implements SearchResponse{
    private boolean result;
    private String error;

    public SearchErrorResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }
}
