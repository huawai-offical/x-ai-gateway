package com.prodigalgal.xaigateway.gateway.core.response;

public record GatewayErrorView(
        String code,
        String message,
        boolean retryable
) {
}
