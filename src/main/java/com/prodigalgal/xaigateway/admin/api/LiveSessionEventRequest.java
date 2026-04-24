package com.prodigalgal.xaigateway.admin.api;

public record LiveSessionEventRequest(
        String eventType,
        String direction,
        String payloadJson,
        Long audioBytes
) {
}
