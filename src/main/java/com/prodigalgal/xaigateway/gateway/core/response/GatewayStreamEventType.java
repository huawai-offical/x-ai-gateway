package com.prodigalgal.xaigateway.gateway.core.response;

public enum GatewayStreamEventType {
    TEXT_DELTA,
    REASONING_DELTA,
    TOOL_CALLS,
    COMPLETED,
    ERROR
}
