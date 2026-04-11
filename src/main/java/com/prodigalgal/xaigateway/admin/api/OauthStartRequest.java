package com.prodigalgal.xaigateway.admin.api;

public record OauthStartRequest(
        Long poolId,
        String redirectPath
) {
}
