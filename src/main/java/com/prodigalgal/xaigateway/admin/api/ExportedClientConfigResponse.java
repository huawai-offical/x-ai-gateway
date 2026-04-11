package com.prodigalgal.xaigateway.admin.api;

public record ExportedClientConfigResponse(
        String accountName,
        String clientFamily,
        String config
) {
}
