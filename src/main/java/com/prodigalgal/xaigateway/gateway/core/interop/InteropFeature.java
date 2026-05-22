package com.prodigalgal.xaigateway.gateway.core.interop;

public enum InteropFeature {
    CHAT_TEXT,
    TOOLS,
    IMAGE_INPUT,
    FILE_INPUT,
    FILE_OBJECT,
    REASONING,
    RESPONSE_OBJECT,
    EMBEDDINGS,
    AUDIO_TRANSCRIPTION,
    AUDIO_SPEECH,
    IMAGE_GENERATION,
    MODERATION,
    UPLOAD_CREATE,
    RERANK,
    VIDEO_GENERATION,
    MUSIC_GENERATION,
    ASYNC_TASK,
    WEB_SEARCH;

    public String wireName() {
        return name().toLowerCase();
    }

    public static InteropFeature fromWireName(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            return CHAT_TEXT;
        }
        for (InteropFeature feature : values()) {
            if (feature.wireName().equalsIgnoreCase(wireName)) {
                return feature;
            }
        }
        return CHAT_TEXT;
    }
}
