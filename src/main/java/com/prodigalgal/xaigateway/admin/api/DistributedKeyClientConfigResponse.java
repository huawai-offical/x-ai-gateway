package com.prodigalgal.xaigateway.admin.api;

public record DistributedKeyClientConfigResponse(
        String keyName,
        String clientFamily,
        String format,
        String maskedKey,
        String warning,
        String config
) {
}
