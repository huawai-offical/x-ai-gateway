package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

public record PublicDocsExampleResponse(
        String client,
        String protocol,
        String language,
        String title,
        String content
) {
}
