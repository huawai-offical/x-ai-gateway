package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.catalog.OpenAiFineTunedModelDeletionService;

public record OpenAiModelDeletionResponse(
        String id,
        String object,
        boolean deleted
) {

    public static OpenAiModelDeletionResponse from(
            OpenAiFineTunedModelDeletionService.DeletedFineTunedModelView model) {
        return new OpenAiModelDeletionResponse(
                model.modelId(),
                "model",
                true
        );
    }
}
