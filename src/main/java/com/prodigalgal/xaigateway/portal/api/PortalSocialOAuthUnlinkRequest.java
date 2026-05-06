package com.prodigalgal.xaigateway.portal.api;

public record PortalSocialOAuthUnlinkRequest(
        Long identityId,
        String externalSubject
) {
}
