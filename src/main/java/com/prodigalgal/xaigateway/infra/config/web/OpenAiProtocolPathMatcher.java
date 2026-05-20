package com.prodigalgal.xaigateway.infra.config.web;

public final class OpenAiProtocolPathMatcher {

    private OpenAiProtocolPathMatcher() {
    }

    public static boolean isOpenAiProtocolPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/v1/")) {
            return false;
        }
        return path.equals("/v1/chat/completions")
                || path.startsWith("/v1/chat/completions/")
                || path.equals("/v1/responses")
                || path.startsWith("/v1/responses/")
                || path.startsWith("/v1/conversations")
                || path.startsWith("/v1/webhooks")
                || path.equals("/v1/completions")
                || path.startsWith("/v1/completions/")
                || path.equals("/v1/embeddings")
                || path.startsWith("/v1/audio/")
                || path.startsWith("/v1/images/")
                || path.equals("/v1/moderations")
                || path.equals("/v1/files")
                || path.startsWith("/v1/files/")
                || path.equals("/v1/uploads")
                || path.startsWith("/v1/uploads/")
                || path.equals("/v1/models")
                || path.startsWith("/v1/models/")
                || path.startsWith("/v1/vector_stores")
                || path.startsWith("/v1/realtime");
    }
}
