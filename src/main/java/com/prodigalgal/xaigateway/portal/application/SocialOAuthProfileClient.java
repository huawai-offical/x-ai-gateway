package com.prodigalgal.xaigateway.portal.application;

public interface SocialOAuthProfileClient {

    boolean supports(SocialOAuthProvider provider);

    default int priority() {
        return 100;
    }

    SocialOAuthProfile exchange(SocialOAuthTokenExchangeRequest request);
}
