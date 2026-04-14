package com.prodigalgal.xaigateway.gateway.core.canonical;

import java.util.Locale;

public enum CanonicalMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    public static CanonicalMessageRole from(String role) {
        if (role == null || role.isBlank()) {
            return USER;
        }
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "system" -> SYSTEM;
            case "assistant", "model" -> ASSISTANT;
            case "tool" -> TOOL;
            default -> USER;
        };
    }
}
