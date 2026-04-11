package com.prodigalgal.xaigateway.admin.api;

public record AccountNetworkBindingRequest(
        Long proxyId,
        Long tlsFingerprintProfileId
) {
}
