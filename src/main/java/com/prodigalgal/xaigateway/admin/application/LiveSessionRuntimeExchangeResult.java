package com.prodigalgal.xaigateway.admin.application;

import java.util.List;
import java.util.Map;

public record LiveSessionRuntimeExchangeResult(
        String adapterName,
        Map<String, String> runtimeMetadata,
        List<LiveSessionRuntimeProviderEvent> providerEvents
) {
}
