package com.prodigalgal.xaigateway.infra.config.web;

public record OpenAiApiErrorResponse(Error error) {

    public record Error(
            String message,
            String type,
            String param,
            String code
    ) {
    }
}
