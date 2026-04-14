package com.prodigalgal.xaigateway.gateway.core.shared;

public enum ExecutionBackend {
    SPRING_AI,
    NATIVE,
    PASSTHROUGH;

    public String wireName() {
        return name().toLowerCase();
    }
}
