package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record LiveSessionConformanceResponse(
        String sessionKey,
        String protocol,
        String status,
        boolean connected,
        boolean streaming,
        boolean closed,
        boolean sseReplayAvailable,
        long inputEventCount,
        long outputEventCount,
        long totalEventCount,
        long inputAudioBytes,
        long outputAudioBytes,
        String transport,
        String conformanceStatus,
        List<String> checks,
        List<String> warnings
) {
}
