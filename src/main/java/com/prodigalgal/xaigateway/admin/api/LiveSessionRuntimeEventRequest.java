package com.prodigalgal.xaigateway.admin.api;

public record LiveSessionRuntimeEventRequest(
        String eventType,
        String payloadJson,
        Long audioBytes
) {
}
