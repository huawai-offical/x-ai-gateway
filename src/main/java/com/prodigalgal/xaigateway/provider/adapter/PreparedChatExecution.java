package com.prodigalgal.xaigateway.provider.adapter;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;

public record PreparedChatExecution<T>(
        ProviderType providerType,
        RouteSelectionResult routeSelectionResult,
        T options
) {
}
