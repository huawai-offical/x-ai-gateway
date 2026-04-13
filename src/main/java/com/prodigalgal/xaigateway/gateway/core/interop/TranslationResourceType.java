package com.prodigalgal.xaigateway.gateway.core.interop;

public enum TranslationResourceType {
    CHAT("chat"),
    RESPONSE("response"),
    EMBEDDING("embedding"),
    AUDIO("audio"),
    IMAGE("image"),
    MODERATION("moderation"),
    FILE("file"),
    UPLOAD("upload"),
    BATCH("batch"),
    TUNING("tuning"),
    REALTIME("realtime"),
    UNKNOWN("unknown");

    private final String wireName;

    TranslationResourceType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static TranslationResourceType fromWireName(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            return UNKNOWN;
        }
        for (TranslationResourceType value : values()) {
            if (value.wireName.equalsIgnoreCase(wireName)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
