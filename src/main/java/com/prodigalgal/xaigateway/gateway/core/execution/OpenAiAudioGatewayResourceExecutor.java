package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
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
                && candidate != null
                && candidate.providerType() == ProviderType.OPENAI_DIRECT;
    }

    @Override
    public ResponseEntity<byte[]> executeBinary(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
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
}
