package com.prodigalgal.xaigateway.gateway.core.auth;

import java.util.List;

public record DistributedKeyView(
        Long id,
        String keyName,
        String keyPrefix,
        String maskedKey,
        List<String> allowedProtocols,
        List<String> allowedModels,
        List<DistributedCredentialBindingView> bindings
) {
}
