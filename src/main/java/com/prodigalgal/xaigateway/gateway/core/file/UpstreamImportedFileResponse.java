package com.prodigalgal.xaigateway.gateway.core.file;

public record UpstreamImportedFileResponse(
        GatewayFileResponse file,
        GatewayFileBindingResponse binding
) {
}
