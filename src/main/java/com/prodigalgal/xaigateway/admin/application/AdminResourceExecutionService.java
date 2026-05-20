package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteResponse;
import com.prodigalgal.xaigateway.admin.api.AdminResourceFileRef;
import com.prodigalgal.xaigateway.admin.api.AdminResourceTemplateResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalFileRef;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
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
            GatewayResourceExecutionResult result = gatewayResourceExecutionService.executeDetailedMultipartJson(
                    canonicalRequest,
                    request.requestedModel(),
                    java.util.Map.of()
            ).block();
            if (result == null || result.jsonResponse() == null) {
                throw new IllegalStateException("资源调试响应为空。");
            }
            ResponseEntity<JsonNode> response = result.jsonResponse();
            return new AdminResourceExecuteResponse(
                    result.requestId(),
                    result.gatewayResourceKey(),
                    response.getStatusCode().is2xxSuccessful() ? compilation.selectionResult() : compilation.selectionResult(),
                    compilation.canonicalPlan(),
                    compilation.canonicalPlan().executionBackend(),
                    request.requestPath(),
                    compilation.canonicalPlan().objectMode(),
                    compilation.canonicalPlan().supportStatus(),
                    compilation.canonicalPlan().degradationLevel(),
                    compilation.canonicalPlan().blockerReasons(),
                    response.getStatusCode().value(),
                    response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString(),
                    response.getBody() == null ? JsonNodeFactory.instance.objectNode() : response.getBody(),
                    response.getBody() == null ? null : response.getBody().toPrettyString(),
                    null,
                    result.canonicalResponse()
            );
        }
        if (isBinaryPath(request.requestPath())) {
            GatewayResourceExecutionResult result = gatewayResourceExecutionService.executeDetailedBinaryJson(canonicalRequest, request.requestedModel());
            ResponseEntity<byte[]> response = result.binaryResponse();
            return new AdminResourceExecuteResponse(
                    result.requestId(),
                    result.gatewayResourceKey(),
                    compilation.selectionResult(),
                    compilation.canonicalPlan(),
                    compilation.canonicalPlan().executionBackend(),
                    request.requestPath(),
                    compilation.canonicalPlan().objectMode(),
                    compilation.canonicalPlan().supportStatus(),
                    compilation.canonicalPlan().degradationLevel(),
                    compilation.canonicalPlan().blockerReasons(),
                    response.getStatusCode().value(),
                    response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString(),
                    null,
                    null,
                    response.getBody() == null ? 0 : response.getBody().length,
                    result.canonicalResponse()
            );
        }

        GatewayResourceExecutionResult result = gatewayResourceExecutionService.executeDetailedJson(canonicalRequest, request.requestedModel());
        ResponseEntity<JsonNode> response = result.jsonResponse();
        return new AdminResourceExecuteResponse(
                result.requestId(),
                result.gatewayResourceKey(),
                compilation.selectionResult(),
                compilation.canonicalPlan(),
                compilation.canonicalPlan().executionBackend(),
                request.requestPath(),
                compilation.canonicalPlan().objectMode(),
                compilation.canonicalPlan().supportStatus(),
                compilation.canonicalPlan().degradationLevel(),
                compilation.canonicalPlan().blockerReasons(),
                response.getStatusCode().value(),
                response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString(),
                response.getBody() == null ? JsonNodeFactory.instance.objectNode() : response.getBody(),
                response.getBody() == null ? null : response.getBody().toPrettyString(),
                null,
                result.canonicalResponse()
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<AdminResourceTemplateResponse> templates() {
        JsonNodeFactory json = JsonNodeFactory.instance;
        return java.util.List.of(
                template("response", "create", "chat_execute", "openai", "POST", "/v1/responses", "gpt-4o-mini",
                        "Responses 调试入口，复用聊天执行 Workbench，保留 request log、trace 与 usage。",
                        json.objectNode().put("model", "gpt-4o-mini").put("input", "用一句话介绍 x-ai-gateway。"),
                        java.util.List.of(), java.util.List.of(), java.util.List.of("requestId", "routeSelection", "usage")),
                template("image", "generate", "resource_execute", "openai", "POST", "/v1/images/generations", "gpt-image-1",
                        "图片生成调试入口，适合验证模型、账号池、错误规则和二进制/JSON 返回。",
                        json.objectNode().put("model", "gpt-image-1").put("prompt", "一只在星图上巡航的机械猫。").put("size", "1024x1024"),
                        java.util.List.of(), java.util.List.of(), java.util.List.of("requestId", "canonicalResponse", "usage")),
                template("audio", "transcribe", "resource_execute", "openai", "POST", "/v1/audio/transcriptions", "whisper-1",
                        "音频转写 multipart 调试入口，使用 fileRefs 选择已上传文件。",
                        json.objectNode().put("model", "whisper-1"),
                        java.util.List.of("model", "language"), java.util.List.of("file"), java.util.List.of("requestId", "canonicalResponse", "error")),
                template("file", "upload", "resource_execute", "openai", "POST", "/v1/files", "gpt-4o-mini",
                        "文件上传调试入口，验证 multipart、purpose、文件绑定和上游资源映射。",
                        json.objectNode().put("purpose", "assistants"),
                        java.util.List.of("purpose"), java.util.List.of("file"), java.util.List.of("gatewayResourceKey", "canonicalResponse")),
                template("cache", "import", "resource_execute", "public_resource", "POST", "/api/v1/caches/import", "gpt-4o-mini",
                        "Gateway Cache 导入调试入口，用于验证 cache 生命周期、命中与 lineage。",
                        json.objectNode().put("providerType", "OPENAI_COMPATIBLE").put("externalCacheRef", "cached_content_xxx").put("model", "gpt-4o-mini"),
                        java.util.List.of(), java.util.List.of(), java.util.List.of("cacheName", "lineage", "usage"))
        );
    }

    private AdminResourceTemplateResponse template(
            String resourceType,
            String operation,
            String executionSurface,
            String protocol,
            String method,
            String requestPath,
            String modelHint,
            String description,
            JsonNode bodyTemplate,
            java.util.List<String> formFields,
            java.util.List<String> fileFields,
            java.util.List<String> resultSignals) {
        return new AdminResourceTemplateResponse(
                resourceType,
                operation,
                executionSurface,
                protocol,
                method,
                requestPath,
                modelHint,
                description,
                bodyTemplate,
                formFields,
                fileFields,
                resultSignals
        );
    }

    private boolean isBinaryPath(String requestPath) {
        return "/v1/audio/speech".equals(requestPath)
                || gatewayRequestFeatureService.normalizePath(requestPath).equals("/v1/files/{fileId}/content");
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
