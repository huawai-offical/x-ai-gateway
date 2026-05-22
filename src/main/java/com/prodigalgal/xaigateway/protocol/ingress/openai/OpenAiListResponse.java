package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.function.Function;

public record OpenAiListResponse<T>(
        String object,
        List<T> data,
        @JsonProperty("has_more")
        boolean hasMore,
        @JsonProperty("first_id")
        String firstId,
        @JsonProperty("last_id")
        String lastId
) {

    public static <T> OpenAiListResponse<T> of(List<T> data) {
        return of(data, false, item -> null);
    }

    public static <T> OpenAiListResponse<T> of(
            List<T> data,
            boolean hasMore,
            Function<T, String> idExtractor) {
        List<T> safeData = data == null ? List.of() : List.copyOf(data);
        String firstId = safeData.isEmpty() ? null : idExtractor.apply(safeData.getFirst());
        String lastId = safeData.isEmpty() ? null : idExtractor.apply(safeData.getLast());
        return new OpenAiListResponse<>("list", safeData, hasMore, firstId, lastId);
    }
}
