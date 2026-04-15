package com.prodigalgal.xaigateway.gateway.core.canonical;

import java.util.List;
import java.util.Map;

public record CanonicalResourceLineage(
        String objectMode,
        String gatewayResourceKey,
        String upstreamObjectId,
        Long credentialId,
        Long siteProfileId,
        String requestModel,
        List<String> parts,
        List<Map<String, Object>> partBindings
) {

    public CanonicalResourceLineage {
        parts = parts == null ? List.of() : List.copyOf(parts);
        partBindings = partBindings == null ? List.of() : List.copyOf(partBindings);
    }
}
