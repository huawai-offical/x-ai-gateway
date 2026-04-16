package com.prodigalgal.xaigateway.gateway.core.canonical;

import org.springframework.http.ResponseEntity;

public record NonChatRenderedResponse(
        ResponseEntity<?> response
) {
    public NonChatRenderedResponse {
        if (response == null) {
            throw new IllegalArgumentException("non-chat rendered response 不能为空。");
        }
    }
}
