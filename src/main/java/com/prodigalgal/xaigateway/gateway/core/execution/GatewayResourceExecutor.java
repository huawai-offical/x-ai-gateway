package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface GatewayResourceExecutor {

    ExecutionBackend backend();

    boolean supports(String requestPath, CatalogCandidateView candidate);

    default ResponseEntity<JsonNode> executeJson(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        throw new IllegalArgumentException("当前资源执行器不支持 JSON 执行。");
    }

    default ResponseEntity<byte[]> executeBinary(
            GatewayResourceExecutionContext context,
            JsonNode requestBody,
            String defaultModel) {
        throw new IllegalArgumentException("当前资源执行器不支持二进制执行。");
    }

    default Mono<ResponseEntity<JsonNode>> executeMultipart(
            GatewayResourceExecutionContext context,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        return Mono.error(new IllegalArgumentException("当前资源执行器不支持 multipart 执行。"));
    }
}
