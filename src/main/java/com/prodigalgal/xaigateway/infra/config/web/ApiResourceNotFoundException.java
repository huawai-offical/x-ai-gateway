package com.prodigalgal.xaigateway.infra.config.web;

public class ApiResourceNotFoundException extends RuntimeException {

    public ApiResourceNotFoundException(String message) {
        super(message);
    }
}
