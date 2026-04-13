package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import java.util.Map;

public record TranslationExecutionResponseMapping(
        RouteSelectionSource selectionSource,
        ProviderFamily providerFamily,
        Long siteProfileId,
        ExecutionKind executionKind,
        InteropCapabilityLevel capabilityLevel,
        String upstreamObjectMode,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy
) {
    public static TranslationExecutionResponseMapping fromLegacy(Map<String, Object> legacy) {
        return new TranslationExecutionResponseMapping(
                null,
                null,
                null,
                null,
                null,
                legacy == null ? null : legacy.get("upstreamObjectMode") instanceof String value ? value : null,
                null,
                null,
                null
        );
    }
}
