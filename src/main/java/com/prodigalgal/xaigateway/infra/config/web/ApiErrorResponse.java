package com.prodigalgal.xaigateway.infra.config.web;

public record ApiErrorResponse(String code, String message, String traceId) {
}
