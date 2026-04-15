package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteResponse;
import com.prodigalgal.xaigateway.admin.api.AdminResourceFileRef;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalFileRef;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Service
@Transactional
public class AdminResourceExecutionService {

    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;

    public AdminResourceExecutionService(
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            GatewayResourceExecutionService gatewayResourceExecutionService) {
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
    }

    public AdminResourceExecuteResponse execute(AdminResourceExecuteRequest request) {
        JsonNode payload = request.body() == null || request.body().isNull()
                ? JsonNodeFactory.instance.objectNode()
                : request.body();
        String method = request.method() == null || request.method().isBlank() ? "POST" : request.method().trim().toUpperCase();
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(method, request.requestPath(), payload);
        if (semantics.resourceType() == com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.CHAT
                || semantics.resourceType() == com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.RESPONSE) {
            throw new IllegalArgumentException("当前接口仅用于资源执行调试，请改用聊天执行调试。");
        }

        var compilation = translationExecutionPlanCompiler.compilePreview(
                request.distributedKeyPrefix(),
                request.protocol(),
                method,
                request.requestPath(),
                request.requestedModel(),
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                payloadForExplain(method, request, payload)
        );
        CanonicalResourceRequest canonicalRequest = new CanonicalResourceRequest(
                request.distributedKeyPrefix(),
                CanonicalIngressProtocol.from(request.protocol()),
                method,
                request.requestPath(),
                gatewayRequestFeatureService.normalizePath(request.requestPath()),
                gatewayRequestFeatureService.extractPathParams(request.requestPath()),
                request.requestedModel(),
                semantics.resourceType(),
                semantics.operation(),
                payload,
                request.formFields() == null ? java.util.Map.of() : java.util.Map.copyOf(request.formFields()),
                toFileRefs(request.fileRefs()),
                isBinaryPath(request.requestPath()),
                false
        );
        if (isMultipartRequest(request)) {
            ResponseEntity<JsonNode> response = gatewayResourceExecutionService.executeMultipartJson(
                    canonicalRequest,
                    request.requestedModel(),
                    java.util.Map.of()
            ).block();
            if (response == null) {
                throw new IllegalStateException("资源调试响应为空。");
            }
            return new AdminResourceExecuteResponse(
                    response.getStatusCode().is2xxSuccessful() ? compilation.selectionResult() : compilation.selectionResult(),
                    compilation.canonicalPlan(),
                    compilation.canonicalPlan().executionBackend(),
                    request.requestPath(),
                    compilation.canonicalPlan().objectMode(),
                    response.getStatusCode().value(),
                    response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString(),
                    response.getBody() == null ? JsonNodeFactory.instance.objectNode() : response.getBody(),
                    response.getBody() == null ? null : response.getBody().toPrettyString(),
                    null
            );
        }
        if (isBinaryPath(request.requestPath())) {
            ResponseEntity<byte[]> response = gatewayResourceExecutionService.executeBinaryJson(canonicalRequest, request.requestedModel());
            return new AdminResourceExecuteResponse(
                    compilation.selectionResult(),
                    compilation.canonicalPlan(),
                    compilation.canonicalPlan().executionBackend(),
                    request.requestPath(),
                    compilation.canonicalPlan().objectMode(),
                    response.getStatusCode().value(),
                    response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString(),
                    null,
                    null,
                    response.getBody() == null ? 0 : response.getBody().length
            );
        }

        ResponseEntity<JsonNode> response = gatewayResourceExecutionService.executeJson(canonicalRequest, request.requestedModel());
        return new AdminResourceExecuteResponse(
                compilation.selectionResult(),
                compilation.canonicalPlan(),
                compilation.canonicalPlan().executionBackend(),
                request.requestPath(),
                compilation.canonicalPlan().objectMode(),
                response.getStatusCode().value(),
                response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString(),
                response.getBody() == null ? JsonNodeFactory.instance.objectNode() : response.getBody(),
                response.getBody() == null ? null : response.getBody().toPrettyString(),
                null
        );
    }

    private boolean isBinaryPath(String requestPath) {
        return "/v1/audio/speech".equals(requestPath);
    }

    private boolean isMultipartRequest(AdminResourceExecuteRequest request) {
        boolean hasMultipartPayload = request.fileRefs() != null && !request.fileRefs().isEmpty()
                || request.formFields() != null && !request.formFields().isEmpty();
        return hasMultipartPayload && ("/v1/audio/transcriptions".equals(request.requestPath())
                || "/v1/audio/translations".equals(request.requestPath())
                || "/v1/images/edits".equals(request.requestPath())
                || "/v1/images/variations".equals(request.requestPath())
                || "/v1/files".equals(request.requestPath())
                || request.requestPath().matches("^/v1/uploads/[^/]+/parts$"));
    }

    private JsonNode payloadForExplain(String method, AdminResourceExecuteRequest request, JsonNode body) {
        if (!isMultipartRequest(request)) {
            return body;
        }
        var payload = JsonNodeFactory.instance.objectNode();
        if (request.requestedModel() != null && !request.requestedModel().isBlank()) {
            payload.put("model", request.requestedModel());
        }
        if (request.formFields() != null) {
            request.formFields().forEach(payload::put);
        }
        if (request.fileRefs() != null && !request.fileRefs().isEmpty()) {
            var files = payload.putArray("fileRefs");
            for (AdminResourceFileRef fileRef : request.fileRefs()) {
                var item = files.addObject();
                item.put("fieldName", fileRef.fieldName());
                item.put("fileKey", fileRef.fileKey());
                if (fileRef.filename() != null) {
                    item.put("filename", fileRef.filename());
                }
                if (fileRef.mimeType() != null) {
                    item.put("mimeType", fileRef.mimeType());
                }
            }
        }
        return payload;
    }

    private java.util.List<CanonicalFileRef> toFileRefs(java.util.List<AdminResourceFileRef> fileRefs) {
        if (fileRefs == null || fileRefs.isEmpty()) {
            return java.util.List.of();
        }
        return fileRefs.stream()
                .map(item -> new CanonicalFileRef(item.fieldName(), item.fileKey(), item.filename(), item.mimeType()))
                .toList();
    }
}
