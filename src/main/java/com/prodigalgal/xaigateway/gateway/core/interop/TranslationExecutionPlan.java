package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import java.util.List;
import java.util.Map;

public record TranslationExecutionPlan(
        boolean executable,
        String resourceType,
        String operation,
        ProviderFamily providerFamily,
        Long siteProfileId,
        ExecutionKind executionKind,
        InteropCapabilityLevel capabilityLevel,
        String upstreamObjectMode,
        List<String> lossReasons,
        List<String> blockedReasons,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        Map<String, Object> requestMapping,
        Map<String, Object> responseMapping
) {
}
