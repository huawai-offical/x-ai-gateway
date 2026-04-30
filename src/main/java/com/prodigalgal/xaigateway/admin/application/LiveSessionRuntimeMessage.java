package com.prodigalgal.xaigateway.admin.application;

public record LiveSessionRuntimeMessage(
        String eventType,
        String payloadJson,
        long audioBytes
) {
}
