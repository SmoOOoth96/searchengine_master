package searchengine.dto.indexing;

import lombok.Getter;

@Getter
public class IndexingSuccessResponse implements IndexingResponse{
    private boolean result;

    public IndexingSuccessResponse(boolean result) {
        this.result = result;
    }
}
