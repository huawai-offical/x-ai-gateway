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
    AUDIO_TRANSLATION,
    AUDIO_SPEECH,
    IMAGE_GENERATION,
    IMAGE_EDIT,
    IMAGE_VARIATION,
    MODERATION,
    UPLOAD_CREATE,
    BATCH_CREATE,
    TUNING_CREATE,
    REALTIME_CLIENT_SECRET;

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
