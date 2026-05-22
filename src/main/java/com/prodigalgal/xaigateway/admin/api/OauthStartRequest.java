package com.prodigalgal.xaigateway.admin.api;

public record OauthStartRequest(
        Long groupId,
        String redirectPath
) {
}
