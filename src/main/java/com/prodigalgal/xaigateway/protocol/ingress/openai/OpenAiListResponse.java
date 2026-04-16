package com.prodigalgal.xaigateway.protocol.ingress.openai;

import java.util.List;

public record OpenAiListResponse<T>(
        String object,
        List<T> data
) {

    public static <T> OpenAiListResponse<T> of(List<T> data) {
        return new OpenAiListResponse<>("list", data);
    }
}
