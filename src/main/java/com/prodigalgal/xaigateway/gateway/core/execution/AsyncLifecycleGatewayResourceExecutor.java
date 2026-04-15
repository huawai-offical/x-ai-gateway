package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalFileRef;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AsyncLifecycleGatewayResourceExecutor implements GatewayResourceExecutor {

    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final ObjectMapper objectMapper;

    public AsyncLifecycleGatewayResourceExecutor(
            GatewayAsyncResourceService gatewayAsyncResourceService,
            ObjectMapper objectMapper) {
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.ORCHESTRATION;
    }

    @Override
    public boolean supports(CanonicalResourceRequest request, CatalogCandidateView candidate) {
        if (request == null || request.normalizedPath() == null) {
            return false;
        }
        return request.normalizedPath().startsWith("/v1/uploads")
                || request.normalizedPath().startsWith("/v1/batches")
                || request.normalizedPath().startsWith("/v1/fine_tuning/jobs")
                || "/v1/realtime/client_secrets".equals(request.normalizedPath());
    }

    @Override
    public ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        Long preferredCredentialId = context.selectionResult() == null ? null : context.selectionResult().selectedCandidate().candidate().credentialId();
        JsonNode response = switch (context.normalizedPath()) {
            case "/v1/uploads" -> gatewayAsyncResourceService.createUpload(context.distributedKeyId(), requestBody, preferredCredentialId);
            case "/v1/uploads/{uploadId}" -> gatewayAsyncResourceService.getUpload(requirePathParam(context, "uploadId"), context.distributedKeyId());
            case "/v1/uploads/{uploadId}/complete" -> gatewayAsyncResourceService.completeUpload(requirePathParam(context, "uploadId"), context.distributedKeyId());
            case "/v1/uploads/{uploadId}/cancel" -> gatewayAsyncResourceService.cancelUpload(requirePathParam(context, "uploadId"), context.distributedKeyId());
            case "/v1/batches" -> gatewayAsyncResourceService.createBatch(context.distributedKeyId(), requestBody, preferredCredentialId);
            case "/v1/batches/{batchId}" -> gatewayAsyncResourceService.getBatch(requirePathParam(context, "batchId"), context.distributedKeyId());
            case "/v1/batches/{batchId}/cancel" -> gatewayAsyncResourceService.cancelBatch(requirePathParam(context, "batchId"), context.distributedKeyId());
            case "/v1/fine_tuning/jobs" -> gatewayAsyncResourceService.createTuning(context.distributedKeyId(), requestBody, preferredCredentialId);
            case "/v1/fine_tuning/jobs/{jobId}" -> gatewayAsyncResourceService.getTuning(requirePathParam(context, "jobId"), context.distributedKeyId());
            case "/v1/fine_tuning/jobs/{jobId}/cancel" -> gatewayAsyncResourceService.cancelTuning(requirePathParam(context, "jobId"), context.distributedKeyId());
            case "/v1/realtime/client_secrets" -> gatewayAsyncResourceService.createRealtimeClientSecret(context.distributedKeyId(), requestBody, preferredCredentialId);
            default -> throw new IllegalArgumentException("当前生命周期对象路径不受支持。");
        };
        return ResponseEntity.ok(response);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> executeMultipart(
            GatewayResourceExecutionContext context,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        if (!"/v1/uploads/{uploadId}/parts".equals(context.normalizedPath())) {
            return Mono.error(new IllegalArgumentException("当前生命周期对象 multipart 路径不受支持。"));
        }
        String uploadId = requirePathParam(context, "uploadId");
        FilePart dataPart = files.get("data");
        if (dataPart != null) {
            return gatewayAsyncResourceService.addUploadPart(uploadId, context.distributedKeyId(), dataPart)
                    .map(ResponseEntity::ok);
        }
        CanonicalFileRef fileRef = context.request().fileRefs().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("upload parts 缺少 data fileRef。"));
        return gatewayAsyncResourceService.addUploadPartFromGatewayFile(uploadId, context.distributedKeyId(), fileRef.fileKey())
                .map(ResponseEntity::ok);
    }

    private String requirePathParam(GatewayResourceExecutionContext context, String key) {
        String value = context.request().pathParams().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少路径参数：" + key);
        }
        return value;
    }
}
