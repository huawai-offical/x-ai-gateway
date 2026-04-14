package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.catalog.GatewayPublicModelView;
import com.prodigalgal.xaigateway.gateway.core.catalog.SurfaceCapabilityView;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OpenAiModelsResponse(
        String object,
        List<ModelItem> data
) {

    public record ModelItem(
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
            @JsonProperty("preferred_backend")
            String preferredBackend,
            @JsonProperty("supported_backends")
            List<String> supportedBackends,
            @JsonProperty("supports_chat")
            boolean supportsChat,
            @JsonProperty("supports_embeddings")
            boolean supportsEmbeddings,
            Map<String, SurfaceCapabilityView> surfaces
    ) {
    }

    public static OpenAiModelsResponse from(List<GatewayPublicModelView> models) {
        long created = Instant.now().getEpochSecond();
        return new OpenAiModelsResponse(
                "list",
                models.stream()
                        .map(model -> new ModelItem(
                                model.publicModelId(),
                                "model",
                                created,
                                model.alias() ? "x-ai-gateway-alias" : "x-ai-gateway",
                                model.providerFamily() == null ? null : model.providerFamily().name().toLowerCase(),
                                model.siteKind() == null ? null : model.siteKind().name().toLowerCase(),
                                (model.capabilityLevel() == null ? InteropCapabilityLevel.NATIVE : model.capabilityLevel()).name().toLowerCase(),
                                model.preferredBackend() == null ? ExecutionBackend.SPRING_AI.wireName() : model.preferredBackend().wireName(),
                                model.supportedBackends().stream().map(ExecutionBackend::wireName).toList(),
                                model.supportsChat(),
                                model.supportsEmbeddings(),
                                model.surfaces()
                        ))
                        .toList()
        );
    }
}
