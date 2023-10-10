package searchengine.dto.searching;

import lombok.Getter;

import java.util.List;

@Getter
public class SearchSuccessResponse implements SearchResponse{
    private boolean result;
    private int count;
    private List<SiteData> data;

    public SearchSuccessResponse(boolean result, int count, List<SiteData> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }
}
