package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;

public interface OAuthSessionRefreshAdapter {

    UpstreamAccountProviderType providerType();

    OAuthSessionRefreshResult refresh(OAuthSessionRefreshRequest request);
}
