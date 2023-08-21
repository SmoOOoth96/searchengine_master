package searchengine.dto.indexing;

import lombok.Getter;

@Getter
public class IndexingErrorResponse implements IndexingResponse{
    private String error;
    private boolean result;
    public IndexingErrorResponse(String error, boolean result) {
        this.error = error;
        this.result = result;
    }
}
