package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import java.util.Map;

public record GatewayResourceExecutionContext(
        Long distributedKeyId,
        RouteSelectionResult selectionResult,
        UpstreamCredentialEntity credential,
        ResolvedCredentialMaterial credentialMaterial,
        CanonicalResourceRequest request,
        CanonicalExecutionPlan executionPlan
) {
    public GatewayResourceExecutionContext(
            Long distributedKeyId,
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            String apiKey,
            CanonicalResourceRequest request
    ) {
        this(distributedKeyId, selectionResult, credential, legacyMaterial(credential, apiKey), request, null);
    }

    public GatewayResourceExecutionContext(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            String apiKey,
            String requestPath
    ) {
        this(
                selectionResult == null ? null : selectionResult.distributedKeyId(),
                selectionResult,
                credential,
                apiKey,
                new CanonicalResourceRequest(
                        selectionResult == null ? null : selectionResult.distributedKeyPrefix(),
                        com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol.OPENAI,
                        requestPath,
                        null,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.UNKNOWN,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.UNKNOWN,
                        null,
                        Map.of(),
                        java.util.List.of(),
                        false
                )
        );
    }

    public GatewayResourceExecutionContext(
            Long distributedKeyId,
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            ResolvedCredentialMaterial credentialMaterial,
            CanonicalResourceRequest request
    ) {
        this(distributedKeyId, selectionResult, credential, credentialMaterial, request, null);
    }

    public String apiKey() {
        return credentialMaterial == null ? null : credentialMaterial.secret();
    }

    public CredentialAuthKind authKind() {
        return credentialMaterial == null ? CredentialAuthKind.API_KEY : credentialMaterial.authKind();
    }

    public String requestPath() {
        return request == null ? null : request.requestPath();
    }

    public String normalizedPath() {
        return request == null ? null : request.normalizedPath();
    }

    public String httpMethod() {
        return request == null ? null : request.httpMethod();
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
