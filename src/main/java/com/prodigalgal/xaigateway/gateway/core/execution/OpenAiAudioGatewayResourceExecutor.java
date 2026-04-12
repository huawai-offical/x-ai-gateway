package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OpenAiAudioGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService;

    public OpenAiAudioGatewayResourceExecutor(GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService) {
        this.gatewayOpenAiPassthroughService = gatewayOpenAiPassthroughService;
    }

    @Override
    public boolean supports(String requestPath, CatalogCandidateView candidate) {
        return requestPath != null
                && requestPath.startsWith("/v1/audio/")
                && candidate != null;
    }

    @Override
    public ResponseEntity<byte[]> executeBinary(
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
        return gatewayOpenAiPassthroughService.executePreparedBinary(
                context.selectionResult(),
                context.credential(),
                siteRequest.client(),
                siteRequest.path(),
                context.requestPath(),
                requestBody
        );
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> executeMultipart(
            GatewayResourceExecutionContext context,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        ensureCompatible(context.selectionResult().selectedCandidate().candidate());
        GatewayOpenAiPassthroughService.CatalogSiteRequest siteRequest = gatewayOpenAiPassthroughService.buildPreparedSiteRequest(
                context.selectionResult(),
                context.credential(),
                context.apiKey(),
                context.requestPath()
        );
        return gatewayOpenAiPassthroughService.prepareMultipartBody(formFields, files)
                .flatMap(body -> gatewayOpenAiPassthroughService.executePreparedMultipart(
                        context.selectionResult(),
                        context.credential(),
                        siteRequest.client(),
                        siteRequest.path(),
                        context.requestPath(),
                        body
                ));
    }

    private void ensureCompatible(CatalogCandidateView candidate) {
        if (candidate.pathStrategy() != PathStrategy.OPENAI_V1) {
            throw new IllegalArgumentException("当前站点路径策略不支持 audio 执行。");
        }
        if (candidate.authStrategy() != AuthStrategy.BEARER
                && candidate.authStrategy() != AuthStrategy.API_KEY_HEADER
                && candidate.authStrategy() != AuthStrategy.API_KEY_QUERY
                && candidate.authStrategy() != AuthStrategy.AZURE_API_KEY) {
            throw new IllegalArgumentException("当前站点鉴权策略不支持 audio 执行。");
        }
    }
}
