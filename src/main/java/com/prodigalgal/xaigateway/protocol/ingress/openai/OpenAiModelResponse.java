package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.catalog.GatewayPublicModelView;
import java.time.Instant;

public record OpenAiModelResponse(
        String id,
        String object,
        long created,
        @JsonProperty("owned_by")
        String ownedBy,
        @JsonProperty("provider_family")
        String providerFamily,
        @JsonProperty("site_kind")
        String siteKind,
        @JsonProperty("capability_level")
        String capabilityLevel,
        @JsonProperty("supports_chat")
        boolean supportsChat,
        @JsonProperty("supports_embeddings")
        boolean supportsEmbeddings
) {

    public static OpenAiModelResponse from(GatewayPublicModelView model) {
        return new OpenAiModelResponse(
                model.publicModelId(),
                "model",
                Instant.now().getEpochSecond(),
                model.alias() ? "x-ai-gateway-alias" : "x-ai-gateway",
                model.providerFamily() == null ? null : model.providerFamily().name().toLowerCase(),
                model.siteKind() == null ? null : model.siteKind().name().toLowerCase(),
                (model.capabilityLevel() == null ? InteropCapabilityLevel.NATIVE : model.capabilityLevel()).name().toLowerCase(),
                model.supportsChat(),
                model.supportsEmbeddings()
        );
    }
}
