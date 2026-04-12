package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class OpenAiModerationsGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService;

    public OpenAiModerationsGatewayResourceExecutor(GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService) {
        this.gatewayOpenAiPassthroughService = gatewayOpenAiPassthroughService;
    }

    @Override
    public boolean supports(String requestPath, CatalogCandidateView candidate) {
        return "/v1/moderations".equals(requestPath)
                && candidate != null
                && candidate.providerType() == ProviderType.OPENAI_DIRECT;
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        GatewayOpenAiPassthroughService.CatalogSiteRequest siteRequest = gatewayOpenAiPassthroughService.buildPreparedSiteRequest(
                context.selectionResult(),
                context.credential(),
                context.apiKey(),
                context.requestPath()
        );
        return gatewayOpenAiPassthroughService.executePreparedJson(
                context.selectionResult(),
                context.credential(),
                siteRequest.client(),
                siteRequest.path(),
                context.requestPath(),
                requestBody
        );
    }
}
