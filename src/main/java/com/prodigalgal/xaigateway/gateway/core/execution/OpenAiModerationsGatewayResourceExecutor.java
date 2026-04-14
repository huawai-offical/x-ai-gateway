package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class OpenAiModerationsGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService;

    public OpenAiModerationsGatewayResourceExecutor(GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService) {
        this.gatewayOpenAiPassthroughService = gatewayOpenAiPassthroughService;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.PASSTHROUGH;
    }

    @Override
    public boolean supports(String requestPath, CatalogCandidateView candidate) {
        return "/v1/moderations".equals(requestPath)
                && candidate != null;
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        ensureCompatible(context.selectionResult().selectedCandidate().candidate());
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

    private void ensureCompatible(CatalogCandidateView candidate) {
        if (candidate.pathStrategy() != PathStrategy.OPENAI_V1) {
            throw new IllegalArgumentException("当前站点路径策略不支持 moderations 执行。");
        }
        if (candidate.authStrategy() != AuthStrategy.BEARER
                && candidate.authStrategy() != AuthStrategy.API_KEY_HEADER
                && candidate.authStrategy() != AuthStrategy.API_KEY_QUERY
                && candidate.authStrategy() != AuthStrategy.AZURE_API_KEY) {
            throw new IllegalArgumentException("当前站点鉴权策略不支持 moderations 执行。");
        }
    }
}
