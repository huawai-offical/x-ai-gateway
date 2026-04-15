package com.prodigalgal.xaigateway.gateway.core.canonical;

import java.util.Map;

public record CanonicalResourceArtifact(
        String artifactKind,
        String artifactId,
        String displayName,
        Map<String, Object> attributes
) {

    public CanonicalResourceArtifact {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
