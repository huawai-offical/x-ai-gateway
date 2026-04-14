package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.AdminResourceExecuteResponse;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
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
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.requestPath(), request.body());
        if (semantics.resourceType() == com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.CHAT
                || semantics.resourceType() == com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.RESPONSE) {
            throw new IllegalArgumentException("当前接口仅用于资源执行调试，请改用聊天执行调试。");
        }

        var compilation = translationExecutionPlanCompiler.compilePreview(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                request.body()
        );
        CanonicalResourceRequest canonicalRequest = new CanonicalResourceRequest(
                request.distributedKeyPrefix(),
                CanonicalIngressProtocol.from(request.protocol()),
                request.requestPath(),
                request.requestedModel(),
                semantics.resourceType(),
                semantics.operation(),
                request.body(),
                java.util.Map.of(),
                java.util.List.of(),
                false
        );
        if (isBinaryPath(request.requestPath())) {
            ResponseEntity<byte[]> response = gatewayResourceExecutionService.executeBinaryJson(canonicalRequest, request.requestedModel());
            return new AdminResourceExecuteResponse(
                    compilation.selectionResult(),
                    compilation.canonicalPlan(),
                    compilation.canonicalPlan().executionBackend(),
                    request.requestPath(),
                    compilation.canonicalPlan().executionBackend().wireName(),
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
                compilation.canonicalPlan().executionBackend().wireName(),
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
}
