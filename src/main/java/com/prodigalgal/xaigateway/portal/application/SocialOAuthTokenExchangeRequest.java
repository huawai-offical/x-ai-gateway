package com.prodigalgal.xaigateway.portal.application;

public record SocialOAuthTokenExchangeRequest(
        SocialOAuthProvider provider,
        String code,
        String state,
        String redirectUri,
        String codeVerifier,
        String externalSubjectHint,
        String emailHint,
        String displayNameHint,
        String metadataJsonHint
) {
}
