package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.catalog.GatewayPublicModelView;
import java.time.Instant;

public record OpenAiModelResponse(
        String id,
        String object,
        long created,
        @JsonProperty("owned_by")
        String ownedBy
) {

    public static OpenAiModelResponse from(GatewayPublicModelView model) {
        return new OpenAiModelResponse(
                model.publicModelId(),
                "model",
                Instant.now().getEpochSecond(),
                model.alias() ? "x-ai-gateway-alias" : "x-ai-gateway"
        );
    }
}
