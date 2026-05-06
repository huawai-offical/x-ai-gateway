package com.prodigalgal.xaigateway.admin.api;

public record MonitoringChannelHealthResponse(
        String providerType,
        long requestCount,
        long failedRequestCount,
        double failureRate,
        long averageDurationMs,
        String status,
        String lastErrorCode,
        String lastErrorMessage
) {
}
