package com.prodigalgal.xaigateway.gateway.core.auth;

public class GatewayUnauthorizedException extends RuntimeException {

    public GatewayUnauthorizedException(String message) {
        super(message);
    }
}
