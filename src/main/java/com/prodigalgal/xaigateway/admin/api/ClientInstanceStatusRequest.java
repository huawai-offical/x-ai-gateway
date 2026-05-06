package com.prodigalgal.xaigateway.admin.api;

public record ClientInstanceStatusRequest(
        Boolean active,
        String reason
) {
}
