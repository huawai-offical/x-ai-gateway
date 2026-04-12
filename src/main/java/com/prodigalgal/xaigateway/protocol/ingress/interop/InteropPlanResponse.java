package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.List;
import java.util.Map;

public record InteropPlanResponse(
        boolean executable,
        String protocol,
        String requestPath,
        String requestedModel,
        String degradationPolicy,
        List<String> requiredFeatures,
        List<String> blockers,
        List<String> degradations,
        String resourceType,
        String operation,
        ProviderFamily providerFamily,
        Long siteProfileId,
        ExecutionKind executionKind,
        String capabilityLevel,
        String upstreamObjectMode,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        RouteSelectionResult selectionResult,
        Map<String, Object> summary,
        Map<String, Object> debug
) {
    public InteropPlanResponse(
            boolean executable,
            String protocol,
            String requestPath,
            String requestedModel,
            String degradationPolicy,
            List<String> requiredFeatures,
            List<String> blockers,
            List<String> degradations,
            String resourceType,
            String operation,
            RouteSelectionResult selectionResult,
            Map<String, Object> summary,
            Map<String, Object> debug
    ) {
        this(
                executable,
                protocol,
                requestPath,
                requestedModel,
                degradationPolicy,
                requiredFeatures,
                blockers,
                degradations,
                resourceType,
                operation,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                selectionResult,
                summary,
                debug
        );
    }
}
