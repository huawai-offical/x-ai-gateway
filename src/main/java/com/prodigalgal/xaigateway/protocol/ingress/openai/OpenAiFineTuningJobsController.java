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
@RequestMapping("/v1/fine_tuning/jobs")
public class OpenAiFineTuningJobsController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;
    private final ObjectMapper objectMapper;

    public OpenAiFineTuningJobsController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayResourceExecutionService gatewayResourceExecutionService,
            ObjectMapper objectMapper) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public JsonNode createTuning(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/fine_tuning/jobs",
                "resource-orchestration",
                requestBody
        );
    }

    @GetMapping
    public JsonNode listTunings(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "GET",
                "/v1/fine_tuning/jobs",
                "resource-orchestration",
                null
        );
    }

    @GetMapping("/{jobId}/events")
    public JsonNode listTuningEvents(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String jobId,
            @RequestParam Map<String, String> query) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "GET",
                "/v1/fine_tuning/jobs/" + jobId + "/events",
                "resource-orchestration",
                queryPayload(query)
        );
    }

    @GetMapping("/{jobId}/checkpoints")
    public JsonNode listTuningCheckpoints(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String jobId,
            @RequestParam Map<String, String> query) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "GET",
                "/v1/fine_tuning/jobs/" + jobId + "/checkpoints",
                "resource-orchestration",
                queryPayload(query)
        );
    }

    @GetMapping("/{jobId}")
    public JsonNode getTuning(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String jobId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "GET",
                "/v1/fine_tuning/jobs/" + jobId,
                "resource-orchestration",
                null
        );
    }

    @PostMapping("/{jobId}/cancel")
    public JsonNode cancelTuning(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String jobId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeLifecycleJson(
                distributedKey.id(),
                distributedKey.keyPrefix(),
                "POST",
                "/v1/fine_tuning/jobs/" + jobId + "/cancel",
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
