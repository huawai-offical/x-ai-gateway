package com.prodigalgal.xaigateway.gateway.core.response;

import java.util.Locale;

public enum GatewayFinishReason {
    STOP,
    TOOL_CALLS,
    END_TURN,
    LENGTH,
    MAX_TOKENS,
    CONTENT_FILTER,
    CANCELED,
    ERROR,
    UNKNOWN;

    public static GatewayFinishReason fromRaw(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return STOP;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "stop" -> STOP;
            case "tool_calls", "tool_use" -> TOOL_CALLS;
            case "end_turn" -> END_TURN;
            case "length" -> LENGTH;
            case "max_tokens", "max_output_tokens" -> MAX_TOKENS;
            case "content_filter" -> CONTENT_FILTER;
            case "canceled", "cancelled" -> CANCELED;
            case "error", "failed" -> ERROR;
            default -> UNKNOWN;
        };
    }
}
