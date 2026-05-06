package com.prodigalgal.xaigateway.portal.application;

public record SocialOAuthProfile(
        SocialOAuthProvider provider,
        String externalSubject,
        String email,
        String displayName,
        String metadataJson
) {
}
