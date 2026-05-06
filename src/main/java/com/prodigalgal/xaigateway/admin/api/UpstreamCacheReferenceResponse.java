package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public record UpstreamCacheReferenceResponse(
        Long id,
        Long distributedKeyId,
        ProviderType providerType,
        Long credentialId,
        String modelGroup,
        String prefixHash,
        String externalCacheRef,
        String status,
        String effectiveStatus,
        boolean expired,
        boolean active,
        Instant expireAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt,
        JsonNode lifecycle
) {
    public UpstreamCacheReferenceResponse(
            Long id,
            Long distributedKeyId,
            ProviderType providerType,
            Long credentialId,
            String modelGroup,
            String prefixHash,
            String externalCacheRef,
            String status,
            Instant expireAt,
            Instant lastUsedAt,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                distributedKeyId,
                providerType,
                credentialId,
                modelGroup,
                prefixHash,
                externalCacheRef,
                status,
                effectiveStatus(status, expireAt),
                isExpired(expireAt),
                isActive(status, expireAt),
                expireAt,
                lastUsedAt,
                createdAt,
                updatedAt,
                lifecycle(status, expireAt, lastUsedAt)
        );
    }

    private static String effectiveStatus(String status, Instant expireAt) {
        return isExpired(expireAt) ? "EXPIRED" : status;
    }

    private static boolean isExpired(Instant expireAt) {
        return expireAt != null && expireAt.isBefore(Instant.now());
    }

    private static boolean isActive(String status, Instant expireAt) {
        return "ACTIVE".equalsIgnoreCase(status) && !isExpired(expireAt);
    }

    private static ObjectNode lifecycle(String status, Instant expireAt, Instant lastUsedAt) {
        ObjectNode lifecycle = JsonNodeFactory.instance.objectNode();
        lifecycle.put("status", status);
        lifecycle.put("effective_status", effectiveStatus(status, expireAt));
        lifecycle.put("expired", isExpired(expireAt));
        lifecycle.put("active", isActive(status, expireAt));
        putInstant(lifecycle, "expire_at", expireAt);
        putInstant(lifecycle, "last_used_at", lastUsedAt);
        return lifecycle;
    }

    private static void putInstant(ObjectNode node, String fieldName, Instant value) {
        if (value == null) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value.toString());
        }
    }
}
