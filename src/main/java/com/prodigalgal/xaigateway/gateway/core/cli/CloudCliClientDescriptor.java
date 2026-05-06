package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import java.util.List;

public record CloudCliClientDescriptor(
        String client,
        GatewayClientFamily clientFamily,
        String protocol,
        String basePath,
        List<String> requiredAuth,
        List<String> optionalMetadataHeaders,
        List<String> notes
) {
}
