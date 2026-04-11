package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.catalog.GatewayPublicModelView;
import java.time.Instant;
import java.util.List;

public record OpenAiModelsResponse(
        String object,
        List<ModelItem> data
) {

    public record ModelItem(
            String id,
            String object,
            long created,
            @JsonProperty("owned_by")
            String ownedBy
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
                                model.alias() ? "x-ai-gateway-alias" : "x-ai-gateway"
                        ))
                        .toList()
        );
    }
}
