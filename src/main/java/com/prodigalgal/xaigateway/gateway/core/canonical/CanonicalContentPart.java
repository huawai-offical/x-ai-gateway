package com.prodigalgal.xaigateway.gateway.core.canonical;

public record CanonicalContentPart(
        CanonicalPartType type,
        String text,
        String mimeType,
        String uri,
        String name,
        String toolCallId,
        String toolName
) {

    public static CanonicalContentPart text(String text) {
        return new CanonicalContentPart(CanonicalPartType.TEXT, text, null, null, null, null, null);
    }

    public static CanonicalContentPart image(String mimeType, String uri, String name) {
        return new CanonicalContentPart(CanonicalPartType.IMAGE, null, mimeType, uri, name, null, null);
    }

    public static CanonicalContentPart file(String mimeType, String uri, String name) {
        return new CanonicalContentPart(CanonicalPartType.FILE, null, mimeType, uri, name, null, null);
    }

    public static CanonicalContentPart toolResult(String toolCallId, String toolName, String text) {
        return new CanonicalContentPart(CanonicalPartType.TOOL_RESULT, text, null, null, null, toolCallId, toolName);
    }
}
