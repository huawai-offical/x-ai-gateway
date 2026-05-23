package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.time.Instant;
import java.util.List;

public record ProviderDomainCatalogResponse(
        Instant generatedAt,
        Summary summary,
        List<Vendor> vendors,
        List<AccountGroup> unassignedAccountGroups
) {
    public record Summary(
            int vendorCount,
            int protocolEndpointCount,
            int accountGroupCount,
            int credentialCount,
            int distributedKeyBindingCount
    ) {
    }

    public record Vendor(
            Long siteProfileId,
            String profileCode,
            String displayName,
            String vendorCode,
            String vendorName,
            ProviderFamily providerFamily,
            UpstreamSiteKind siteKind,
            boolean active,
            String healthState,
            long linkedCredentialCount,
            int modelCount,
            List<ProtocolEndpoint> protocolEndpoints,
            List<AccountGroup> accountGroups
    ) {
    }

    public record ProtocolEndpoint(
            Long id,
            String endpointCode,
            String displayName,
            String protocolSuite,
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String baseUrl,
            boolean active,
            long linkedCredentialCount,
            List<Long> accountGroupIds
    ) {
    }

    public record AccountGroup(
            Long id,
            String groupName,
            UpstreamAccountProviderType providerType,
            String groupKind,
            String groupKindSource,
            boolean defaultGroup,
            boolean active,
            List<String> supportedModels,
            List<String> supportedProtocols,
            List<String> allowedClientFamilies,
            long apiCredentialCount,
            List<EndpointCoverage> endpointCoverage,
            List<Credential> credentials,
            List<DistributedKeyBinding> distributedKeyBindings
    ) {
    }

    public record EndpointCoverage(
            Long endpointId,
            String endpointCode,
            String displayName,
            String protocolSuite,
            long credentialCount,
            String source
    ) {
    }

    public record Credential(
            Long id,
            String credentialName,
            ProviderType providerType,
            Long siteProfileId,
            Long protocolEndpointId,
            Long groupId,
            boolean active,
            boolean cooldown,
            String status,
            int supportedModelCount,
            String lastErrorCode,
            String lastErrorMessage,
            Instant cooldownUntil,
            Instant lastUsedAt
    ) {
    }

    public record DistributedKeyBinding(
            Long bindingId,
            Long distributedKeyId,
            String keyName,
            String keyPrefix,
            ProviderType providerType,
            int priority,
            boolean bindingActive,
            boolean distributedKeyActive
    ) {
    }
}
