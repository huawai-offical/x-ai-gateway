package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/batches")
public class OpenAiBatchesController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;
    private final ObjectMapper objectMapper;

    public OpenAiBatchesController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayResourceExecutionService gatewayResourceExecutionService,
            ObjectMapper objectMapper) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public JsonNode listBatches(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam Map<String, String> query) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "GET",
                "/v1/batches",
                "resource-orchestration",
                queryPayload(query)
        );
    }

    @PostMapping
    public JsonNode createBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/batches",
                "resource-orchestration",
                requestBody
        );
    }

    @GetMapping("/{batchId}")
    public JsonNode getBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String batchId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "GET",
                "/v1/batches/" + batchId,
                "resource-orchestration",
                null
        );
    }

    @PostMapping("/{batchId}/cancel")
    public JsonNode cancelBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String batchId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/batches/" + batchId + "/cancel",
                "resource-orchestration",
                null
        );
    }

    private ObjectNode queryPayload(Map<String, String> query) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (query == null || query.isEmpty()) {
            return payload;
        }
        if (query.containsKey("after")) {
            payload.put("after", query.get("after"));
        }
        if (query.containsKey("limit")) {
            payload.put("limit", query.get("limit"));
        }
        return payload;
    }
}
