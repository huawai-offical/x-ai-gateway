package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record SecurityScanResponse(
        boolean allowed,
        String reason,
        List<String> matchedWords,
        String host
) {
    public SecurityScanResponse {
        matchedWords = matchedWords == null ? List.of() : List.copyOf(matchedWords);
    }
}
