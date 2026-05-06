package com.prodigalgal.xaigateway.portal.application;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class LocalSocialOAuthProfileClient implements SocialOAuthProfileClient {

    @Override
    public boolean supports(SocialOAuthProvider provider) {
        return provider != null;
    }

    @Override
    public int priority() {
        return 1_000;
    }

    @Override
    public SocialOAuthProfile exchange(SocialOAuthTokenExchangeRequest request) {
        String subject = defaultString(
                request.externalSubjectHint(),
                request.provider().wireName() + ":" + normalizeSubjectPart(request.code())
        );
        String email = request.emailHint();
        if (email != null && !email.isBlank()) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
        String displayName = request.displayNameHint();
        String metadataJson = defaultString(
                request.metadataJsonHint(),
                "{\"exchange\":\"local\",\"provider\":\"" + request.provider().wireName() + "\"}"
        );
        return new SocialOAuthProfile(
                request.provider(),
                subject,
                email,
                displayName,
                metadataJson
        );
    }

    private String normalizeSubjectPart(String value) {
        String normalized = value == null ? "missing_code" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return normalized.isBlank() ? "missing_code" : normalized;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
