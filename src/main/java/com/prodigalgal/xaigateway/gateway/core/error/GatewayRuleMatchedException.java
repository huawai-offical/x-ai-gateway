package com.prodigalgal.xaigateway.gateway.core.error;

public class GatewayRuleMatchedException extends RuntimeException {

    private final int status;
    private final String code;

    public GatewayRuleMatchedException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
