package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceArtifact;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLifecycle;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceLineage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceTransition;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record AsyncResourceDetailResponse(
        CanonicalResourceLifecycle lifecycle,
        List<CanonicalResourceTransition> transitions,
        CanonicalResourceLineage lineage,
        List<CanonicalResourceArtifact> artifacts,
        JsonNode requestPayloadJson,
        JsonNode responsePayloadJson,
        JsonNode metadataJson
) {
}
