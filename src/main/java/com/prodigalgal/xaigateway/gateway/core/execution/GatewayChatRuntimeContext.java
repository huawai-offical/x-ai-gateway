package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalChatMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalChatExecutionRequestAdapter;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import java.util.Map;

public record GatewayChatRuntimeContext(
        RouteSelectionResult selectionResult,
        UpstreamCredentialEntity credential,
        ResolvedCredentialMaterial credentialMaterial,
        CanonicalRequest canonicalRequest,
        CanonicalExecutionPlan canonicalExecutionPlan
) {
    public GatewayChatRuntimeContext(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            String apiKey,
            ChatExecutionRequest request
    ) {
        this(
                selectionResult,
                credential,
                legacyMaterial(credential, apiKey),
                new CanonicalChatMapper(new tools.jackson.databind.ObjectMapper()).toCanonicalRequest(request),
                null
        );
    }

    public GatewayChatRuntimeContext(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            ResolvedCredentialMaterial credentialMaterial,
            ChatExecutionRequest request
    ) {
        this(
                selectionResult,
                credential,
                credentialMaterial,
                new CanonicalChatMapper(new tools.jackson.databind.ObjectMapper()).toCanonicalRequest(request),
                null
        );
    }

    public String apiKey() {
        return credentialMaterial == null ? null : credentialMaterial.secret();
    }

    public CredentialAuthKind authKind() {
        return credentialMaterial == null ? CredentialAuthKind.API_KEY : credentialMaterial.authKind();
    }

    public ChatExecutionRequest request() {
        return new CanonicalChatExecutionRequestAdapter().toExecutionRequest(canonicalRequest);
    }

    private static ResolvedCredentialMaterial legacyMaterial(UpstreamCredentialEntity credential, String secret) {
        return new ResolvedCredentialMaterial(
                credential == null ? null : credential.getId(),
                credential == null ? null : credential.getSiteProfileId(),
                CredentialAuthKind.API_KEY,
                secret,
                null,
                Map.of(),
                null,
                "legacy"
        );
    }
}
