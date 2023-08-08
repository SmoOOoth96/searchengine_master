package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IndexingErrorResponse {
    private boolean result;
    private String error;
}
