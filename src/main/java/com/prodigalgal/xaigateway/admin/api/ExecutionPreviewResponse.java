package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.util.Map;

public record ExecutionPreviewResponse(
        ProviderType providerType,
        RouteSelectionResult routeSelection,
        Map<String, Object> providerOptions
) {
}
