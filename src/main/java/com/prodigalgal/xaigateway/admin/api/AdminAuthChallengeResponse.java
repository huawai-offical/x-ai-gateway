package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AdminAuthChallengeResponse(
        String challengeId,
        String mathPrompt,
        Instant issuedAt,
        Instant expiresAt,
        String powAlgorithm,
        String powSalt,
        int powDifficulty
) {
}
