package com.prodigalgal.xaigateway.gateway.core.interop;

public enum InteropFeature {
    CHAT_TEXT,
    TOOLS,
    IMAGE_INPUT,
    FILE_INPUT,
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
}
