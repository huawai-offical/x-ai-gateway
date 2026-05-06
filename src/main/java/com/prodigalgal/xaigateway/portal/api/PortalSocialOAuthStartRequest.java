package com.prodigalgal.xaigateway.portal.api;

import java.util.List;

public record PortalSocialOAuthStartRequest(
        String clientId,
        String redirectPath,
        List<String> scopes
) {
}
