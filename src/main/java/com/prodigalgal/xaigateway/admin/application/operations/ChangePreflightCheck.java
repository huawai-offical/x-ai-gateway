package com.prodigalgal.xaigateway.admin.application.operations;

public record ChangePreflightCheck(
        String checkName,
        String status,
        boolean blocking,
        String message
) {
}
