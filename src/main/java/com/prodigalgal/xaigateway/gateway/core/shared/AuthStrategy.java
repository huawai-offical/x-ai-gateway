package com.prodigalgal.xaigateway.gateway.core.shared;

public enum AuthStrategy {
    BEARER,
    API_KEY_HEADER,
    API_KEY_QUERY,
    AZURE_API_KEY,
    UNSUPPORTED
}
