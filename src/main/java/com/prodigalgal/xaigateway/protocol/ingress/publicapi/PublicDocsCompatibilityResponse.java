package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import java.util.List;

public record PublicDocsCompatibilityResponse(
        String protocol,
        String basePath,
        List<String> compatibleClients,
        List<String> supportedOperations,
        String notes
) {
}
