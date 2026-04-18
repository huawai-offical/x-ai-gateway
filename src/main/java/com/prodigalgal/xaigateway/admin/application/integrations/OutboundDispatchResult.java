package com.prodigalgal.xaigateway.admin.application.integrations;

public record OutboundDispatchResult(
        boolean succeeded,
        Integer responseCode,
        String responseSummary,
        String errorMessage
) {
}
