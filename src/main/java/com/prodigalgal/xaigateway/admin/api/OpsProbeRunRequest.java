package com.prodigalgal.xaigateway.admin.api;

public record OpsProbeRunRequest(
        String probeName,
        String targetUrl,
        String source,
        Boolean forceFailure,
        String detailJson
) {
}
