package com.prodigalgal.xaigateway.portal.api;

public record PortalPasskeyAssertionFinishRequest(
        String challengeId,
        String credentialId,
        String clientDataJson,
        String authenticatorDataBase64,
        String signatureBase64
) {
}
