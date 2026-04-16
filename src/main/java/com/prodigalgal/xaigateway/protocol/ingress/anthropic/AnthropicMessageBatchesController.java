package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Validated
@RestController
@RequestMapping("/v1/messages/batches")
public class AnthropicMessageBatchesController {

    private static final String API_KEY_HEADER = "x-api-key";

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;
    private final AnthropicMessageBatchesRequestMapper anthropicMessageBatchesRequestMapper;
    private final AnthropicMessageBatchesEncoder anthropicMessageBatchesEncoder;

    public AnthropicMessageBatchesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayResourceExecutionService gatewayResourceExecutionService,
            AnthropicMessageBatchesRequestMapper anthropicMessageBatchesRequestMapper,
            AnthropicMessageBatchesEncoder anthropicMessageBatchesEncoder) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
        this.anthropicMessageBatchesRequestMapper = anthropicMessageBatchesRequestMapper;
        this.anthropicMessageBatchesEncoder = anthropicMessageBatchesEncoder;
    }

    @PostMapping
    public ResponseEntity<JsonNode> createBatch(
            @RequestHeader(API_KEY_HEADER) String apiKey,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateRawToken(apiKey);
        JsonNode payload = anthropicMessageBatchesRequestMapper.toCreatePayload(requestBody);
        String model = anthropicMessageBatchesRequestMapper.extractModel(requestBody);
        CanonicalResourceRequest request = new CanonicalResourceRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                "POST",
                "/v1/messages/batches",
                "/v1/messages/batches",
                Map.of(),
                model,
                TranslationResourceType.BATCH,
                TranslationOperation.ANTHROPIC_MESSAGE_BATCH_CREATE,
                payload,
                Map.of(),
                List.of(),
                false,
                false
        );
        JsonNode response = gatewayResourceExecutionService.executeDetailedJson(request, distributedKey.id(), model).responseJson();
        return ResponseEntity.ok(anthropicMessageBatchesEncoder.encode(response));
    }

    @GetMapping("/{messageBatchId}")
    public ResponseEntity<JsonNode> getBatch(
            @RequestHeader(API_KEY_HEADER) String apiKey,
            @PathVariable String messageBatchId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateRawToken(apiKey);
        CanonicalResourceRequest request = new CanonicalResourceRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                "GET",
                "/v1/messages/batches/" + messageBatchId,
                "/v1/messages/batches/{messageBatchId}",
                Map.of("messageBatchId", messageBatchId),
                "resource-orchestration",
                TranslationResourceType.BATCH,
                TranslationOperation.ANTHROPIC_MESSAGE_BATCH_GET,
                anthropicMessageBatchesRequestMapper.emptyPayload(),
                Map.of(),
                List.of(),
                false,
                false
        );
        JsonNode response = gatewayResourceExecutionService.executeDetailedJson(request, distributedKey.id(), "resource-orchestration").responseJson();
        return ResponseEntity.ok(anthropicMessageBatchesEncoder.encode(response));
    }

    @PostMapping("/{messageBatchId}/cancel")
    public ResponseEntity<JsonNode> cancelBatch(
            @RequestHeader(API_KEY_HEADER) String apiKey,
            @PathVariable String messageBatchId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateRawToken(apiKey);
        CanonicalResourceRequest request = new CanonicalResourceRequest(
                distributedKey.keyPrefix(),
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                "POST",
                "/v1/messages/batches/" + messageBatchId + "/cancel",
                "/v1/messages/batches/{messageBatchId}/cancel",
                Map.of("messageBatchId", messageBatchId),
                "resource-orchestration",
                TranslationResourceType.BATCH,
                TranslationOperation.ANTHROPIC_MESSAGE_BATCH_CANCEL,
                anthropicMessageBatchesRequestMapper.emptyPayload(),
                Map.of(),
                List.of(),
                false,
                false
        );
        JsonNode response = gatewayResourceExecutionService.executeDetailedJson(request, distributedKey.id(), "resource-orchestration").responseJson();
        return ResponseEntity.ok(anthropicMessageBatchesEncoder.encode(response));
    }
}
