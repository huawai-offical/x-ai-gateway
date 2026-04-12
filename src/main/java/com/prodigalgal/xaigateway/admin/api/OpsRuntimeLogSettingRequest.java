package com.prodigalgal.xaigateway.admin.api;

public record OpsRuntimeLogSettingRequest(
        String loggerName,
        String logLevel,
        Boolean payloadLoggingEnabled,
        Boolean enabled
) {
}
