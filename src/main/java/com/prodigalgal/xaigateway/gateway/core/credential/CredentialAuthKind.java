package com.prodigalgal.xaigateway.gateway.core.credential;

public enum CredentialAuthKind {
    API_KEY,
    ACCESS_TOKEN,
    GOOGLE_ACCESS_TOKEN;

    public static CredentialAuthKind defaultValue(CredentialAuthKind value) {
        return value == null ? API_KEY : value;
    }
}
