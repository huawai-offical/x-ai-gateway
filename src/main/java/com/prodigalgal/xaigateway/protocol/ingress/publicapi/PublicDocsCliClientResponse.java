package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import java.util.List;

public record PublicDocsCliClientResponse(
        String client,
        String clientFamily,
        String protocol,
        String basePath,
        List<String> requiredAuth,
        List<String> optionalMetadataHeaders,
        List<String> notes
) {
}
