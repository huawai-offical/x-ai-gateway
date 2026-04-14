package com.prodigalgal.xaigateway.gateway.core.catalog;

import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionView;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;
import java.util.Map;

public record GatewayPublicModelView(
        String publicModelId,
        String resolvedModelKey,
        boolean alias,
        Long siteProfileId,
        ProviderFamily providerFamily,
        UpstreamSiteKind siteKind,
        InteropCapabilityLevel capabilityLevel,
        ExecutionBackend preferredBackend,
        List<ExecutionBackend> supportedBackends,
        boolean supportsChat,
        boolean supportsEmbeddings,
        Map<String, CapabilityResolutionView> capabilities,
        Map<String, SurfaceCapabilityView> surfaces
) {
    public GatewayPublicModelView(
            String publicModelId,
            String resolvedModelKey,
            boolean alias
    ) {
        this(publicModelId, resolvedModelKey, alias, null, null, null, InteropCapabilityLevel.NATIVE, ExecutionBackend.SPRING_AI, List.of(), true, false, Map.of(), Map.of());
    }
}
