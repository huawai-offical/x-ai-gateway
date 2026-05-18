package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.OpenAiFineTunedModelDeletionService;
import com.prodigalgal.xaigateway.infra.config.web.ApiResourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/models")
public class OpenAiModelsController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final ModelCatalogQueryService modelCatalogQueryService;
    private final OpenAiFineTunedModelDeletionService openAiFineTunedModelDeletionService;

    public OpenAiModelsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            DistributedKeyQueryService distributedKeyQueryService,
            ModelCatalogQueryService modelCatalogQueryService,
            OpenAiFineTunedModelDeletionService openAiFineTunedModelDeletionService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.modelCatalogQueryService = modelCatalogQueryService;
        this.openAiFineTunedModelDeletionService = openAiFineTunedModelDeletionService;
    }

    @GetMapping
    public OpenAiModelsResponse list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        DistributedKeyView distributedKeyView = resolveDistributedKey(authorization);
        return OpenAiModelsResponse.from(modelCatalogQueryService.listAccessiblePublicModels(
                distributedKeyView,
                "openai"
        ));
    }

    @GetMapping("/{modelId}")
    public OpenAiModelResponse get(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String modelId) {
        DistributedKeyView distributedKeyView = resolveDistributedKey(authorization);
        return modelCatalogQueryService.findAccessiblePublicModel(distributedKeyView, "openai", modelId)
                .map(OpenAiModelResponse::from)
                .orElseThrow(() -> new ApiResourceNotFoundException("未找到指定模型。"));
    }

    @DeleteMapping("/{modelId}")
    public OpenAiModelDeletionResponse delete(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String modelId) {
        DistributedKeyView distributedKeyView = resolveDistributedKey(authorization);
        return OpenAiModelDeletionResponse.from(
                openAiFineTunedModelDeletionService.deleteRegisteredFineTunedModel(distributedKeyView, modelId)
        );
    }

    private DistributedKeyView resolveDistributedKey(String authorization) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return distributedKeyQueryService.findActiveByKeyPrefix(distributedKey.keyPrefix())
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
    }
}
