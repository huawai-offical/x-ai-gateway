package com.prodigalgal.xaigateway.gateway.core.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record GatewayFileResponse(
        String id,
        String object,
        String filename,
        String purpose,
        long bytes,
        @JsonProperty("created_at")
        long createdAt,
        String status
) {

    public static GatewayFileResponse from(
            String fileKey,
            String filename,
            String purpose,
            long bytes,
            Instant createdAt,
            String status
    ) {
        return new GatewayFileResponse(
                fileKey,
                "file",
                filename,
                purpose,
                bytes,
                createdAt.getEpochSecond(),
                status
        );
    }
}
