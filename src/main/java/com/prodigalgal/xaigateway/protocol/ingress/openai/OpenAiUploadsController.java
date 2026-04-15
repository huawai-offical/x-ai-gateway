package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1/uploads")
public class OpenAiUploadsController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;

    public OpenAiUploadsController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayResourceExecutionService gatewayResourceExecutionService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
    }

    @PostMapping
    public JsonNode createUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/uploads",
                requestBody == null ? null : requestBody.path("model").asText("resource-orchestration"),
                requestBody
        );
    }

    @GetMapping("/{uploadId}")
    public JsonNode getUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "GET",
                "/v1/uploads/" + uploadId,
                "resource-orchestration",
                null
        );
    }

    @PostMapping(path = "/{uploadId}/parts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<JsonNode> addPart(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId,
            @RequestPart("data") FilePart data) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleMultipart(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/uploads/" + uploadId + "/parts",
                "resource-orchestration",
                java.util.Map.of(),
                java.util.List.of(),
                java.util.Map.of("data", data)
        );
    }

    @PostMapping("/{uploadId}/complete")
    public JsonNode completeUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/uploads/" + uploadId + "/complete",
                "resource-orchestration",
                null
        );
    }

    @PostMapping("/{uploadId}/cancel")
    public JsonNode cancelUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/uploads/" + uploadId + "/cancel",
                "resource-orchestration",
                null
        );
    }
}
