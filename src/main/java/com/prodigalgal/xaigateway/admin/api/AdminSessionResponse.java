package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AdminSessionResponse(
        boolean authenticated,
        String username,
        Instant authenticatedAt,
        Instant expiresAt
) {

    public static AdminSessionResponse unauthenticated() {
        return new AdminSessionResponse(false, null, null, null);
    }
}
