package com.prodigalgal.xaigateway.admin.application;

public record LiveSessionRuntimeProviderEvent(
        String eventType,
        String payloadJson,
        long audioBytes
) {
}
