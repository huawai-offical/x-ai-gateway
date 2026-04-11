package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.infra.config.web.ApiResourceNotFoundException;
import org.springframework.http.HttpHeaders;
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

    public OpenAiModelsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            DistributedKeyQueryService distributedKeyQueryService,
            ModelCatalogQueryService modelCatalogQueryService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.modelCatalogQueryService = modelCatalogQueryService;
    }

    @GetMapping
    public OpenAiModelsResponse list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        DistributedKeyView distributedKeyView = distributedKeyQueryService.findActiveByKeyPrefix(distributedKey.keyPrefix())
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));
        return OpenAiModelsResponse.from(modelCatalogQueryService.listAccessiblePublicModels(
                distributedKeyView,
                "openai"
        ));
    }

    @GetMapping("/{modelId}")
    public OpenAiModelResponse get(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String modelId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        DistributedKeyView distributedKeyView = distributedKeyQueryService.findActiveByKeyPrefix(distributedKey.keyPrefix())
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"));

        return modelCatalogQueryService.findAccessiblePublicModel(distributedKeyView, "openai", modelId)
                .map(OpenAiModelResponse::from)
                .orElseThrow(() -> new ApiResourceNotFoundException("未找到指定模型。"));
    }
}
