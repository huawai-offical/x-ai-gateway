package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ModelAddressingStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderProtocolEndpointRequest(
        @NotBlank(message = "endpointCode 不能为空。")
        String endpointCode,
        @NotBlank(message = "displayName 不能为空。")
        String displayName,
        @NotBlank(message = "protocolSuite 不能为空。")
        String protocolSuite,
        @NotNull(message = "providerType 不能为空。")
        ProviderType providerType,
        @NotNull(message = "siteKind 不能为空。")
        UpstreamSiteKind siteKind,
        @NotBlank(message = "baseUrl 不能为空。")
        String baseUrl,
        AuthStrategy authStrategy,
        PathStrategy pathStrategy,
        ModelAddressingStrategy modelAddressingStrategy,
        ErrorSchemaStrategy errorSchemaStrategy,
        String streamTransport,
        Object conversationProfile,
        Boolean active
) {
}
