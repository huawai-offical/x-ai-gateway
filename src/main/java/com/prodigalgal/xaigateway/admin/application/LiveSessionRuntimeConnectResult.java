package com.prodigalgal.xaigateway.admin.application;

import java.util.List;
import java.util.Map;

public record LiveSessionRuntimeConnectResult(
        String adapterName,
        String upstreamResumeHandle,
        Map<String, String> runtimeMetadata,
        List<LiveSessionRuntimeProviderEvent> providerEvents
) {
}
