package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OpenAiAudioGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService;
    private final GatewayFileService gatewayFileService;

    public OpenAiAudioGatewayResourceExecutor(
            GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService,
            GatewayFileService gatewayFileService) {
        this.gatewayOpenAiPassthroughService = gatewayOpenAiPassthroughService;
        this.gatewayFileService = gatewayFileService;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.PASSTHROUGH;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        return request != null
                && request.normalizedPath() != null
                && request.normalizedPath().startsWith("/v1/audio/")
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
        return gatewayOpenAiPassthroughService.prepareMultipartBody(formFields, files, gatewayFiles(context))
                .flatMap(body -> gatewayOpenAiPassthroughService.executePreparedMultipart(
                        context.selectionResult(),
                        context.credential(),
                        siteRequest.client(),
                        siteRequest.path(),
                        context.requestPath(),
                        body
                ));
    }

    private Map<String, GatewayFileContent> gatewayFiles(GatewayResourceExecutionContext context) {
        Map<String, GatewayFileContent> gatewayFiles = new LinkedHashMap<>();
        for (var fileRef : context.request().fileRefs()) {
            gatewayFiles.put(fileRef.fieldName(), gatewayFileService.getFileContent(fileRef.fileKey(), context.distributedKeyId()));
        }
        return gatewayFiles;
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
