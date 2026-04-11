package com.prodigalgal.xaigateway.admin.api;

public record OauthStartResponse(
        String sessionKey,
        String authorizationUrl
) {
}
