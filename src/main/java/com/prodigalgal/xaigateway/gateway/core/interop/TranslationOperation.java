package com.prodigalgal.xaigateway.gateway.core.interop;

public enum TranslationOperation {
    CHAT_COMPLETION("chat_completion"),
    RESPONSE_CREATE("response_create"),
    EMBEDDING_CREATE("embedding_create"),
    AUDIO_TRANSCRIPTION("audio_transcription"),
    AUDIO_TRANSLATION("audio_translation"),
    AUDIO_SPEECH("audio_speech"),
    IMAGE_GENERATION("image_generation"),
    IMAGE_EDIT("image_edit"),
    IMAGE_VARIATION("image_variation"),
    MODERATION_CREATE("moderation_create"),
    FILE_CREATE("file_create"),
    UPLOAD_CREATE("upload_create"),
    UPLOAD_PART_ADD("upload_part_add"),
    UPLOAD_COMPLETE("upload_complete"),
    UPLOAD_CANCEL("upload_cancel"),
    BATCH_CREATE("batch_create"),
    BATCH_CANCEL("batch_cancel"),
    TUNING_CREATE("tuning_create"),
    TUNING_CANCEL("tuning_cancel"),
    REALTIME_CLIENT_SECRET_CREATE("realtime_client_secret_create"),
    UNKNOWN("unknown");

    private final String wireName;

    TranslationOperation(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static TranslationOperation fromWireName(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            return UNKNOWN;
        }
        for (TranslationOperation value : values()) {
            if (value.wireName.equalsIgnoreCase(wireName)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
