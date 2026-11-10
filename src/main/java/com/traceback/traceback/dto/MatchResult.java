
package com.traceback.traceback.dto;

import java.util.List;

public record MatchResult(
        ReportResponse matchedItem,
        int matchPercentage,
        List<String> reasons
) {
}
