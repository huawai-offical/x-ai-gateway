package com.prodigalgal.xaigateway.gateway.core.canonical;

public enum CanonicalStreamEventType {
    TEXT_DELTA,
    REASONING_DELTA,
    TOOL_CALLS,
    RAW_SSE,
    COMPLETED,
    ERROR
}
