package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;

public record GatewayChatRuntimeContext(
        RouteSelectionResult selectionResult,
        UpstreamCredentialEntity credential,
        ResolvedCredentialMaterial credentialMaterial,
        CanonicalRequest canonicalRequest,
        CanonicalExecutionPlan canonicalExecutionPlan
) {
    public String apiKey() {
        return credentialMaterial == null ? null : credentialMaterial.secret();
    }
}
