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
        String metadataJsonHint,
        String clientId,
        String clientSecret,
        String tokenEndpoint,
        String userInfoEndpoint,
        String jwksUri
) {
    public SocialOAuthTokenExchangeRequest(
            SocialOAuthProvider provider,
            String code,
            String state,
            String redirectUri,
            String codeVerifier,
            String externalSubjectHint,
            String emailHint,
            String displayNameHint,
            String metadataJsonHint) {
        this(
                provider,
                code,
                state,
                redirectUri,
                codeVerifier,
                externalSubjectHint,
                emailHint,
                displayNameHint,
                metadataJsonHint,
                null,
                null,
                null,
                null,
                null
        );
    }
}
