package com.prodigalgal.xaigateway.gateway.core.observability;

public enum RequestTraceStage {
    DOWNSTREAM_REQUEST,
    CANONICAL_REQUEST,
    TRANSLATION_PLAN,
    UPSTREAM_REQUEST,
    UPSTREAM_RESPONSE,
    DOWNSTREAM_RESPONSE,
    ERROR,
    CUSTOM
}
