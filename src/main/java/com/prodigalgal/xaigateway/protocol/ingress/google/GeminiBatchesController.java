package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping
public class GeminiBatchesController {

    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayFileService gatewayFileService;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final GeminiBatchesRequestMapper geminiBatchesRequestMapper;
    private final GeminiBatchesEncoder geminiBatchesEncoder;

    public GeminiBatchesController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayFileService gatewayFileService,
            GatewayAsyncResourceService gatewayAsyncResourceService,
            GeminiBatchesRequestMapper geminiBatchesRequestMapper,
            GeminiBatchesEncoder geminiBatchesEncoder) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayFileService = gatewayFileService;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.geminiBatchesRequestMapper = geminiBatchesRequestMapper;
        this.geminiBatchesEncoder = geminiBatchesEncoder;
    }

    @PostMapping("/v1beta/models/{model}:batchGenerateContent")
    public JsonNode createBatch(
            @PathVariable String model,
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        String externalFileId = normalizeFileName(geminiBatchesRequestMapper.extractInputFileName(requestBody));
        String gatewayFileKey = gatewayFileService.resolveGatewayFileKeyByGoogleFileName(externalFileId, distributedKey.id());
        Long preferredCredentialId = gatewayFileService.resolveGoogleCredentialIdForFileName(externalFileId, distributedKey.id());
        JsonNode response = gatewayAsyncResourceService.createBatch(
                distributedKey.id(),
                geminiBatchesRequestMapper.toBatchCreatePayload(model, requestBody, gatewayFileKey),
                preferredCredentialId
        );
        return geminiBatchesEncoder.encode(gatewayAsyncResourceService.getBatchView(response.path("id").asText(), distributedKey.id()));
    }

    @GetMapping({"/v1beta/batches/{batchName}", "/v1beta/batches/batches/{batchName}"})
    public JsonNode getBatch(
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String batchName) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        return geminiBatchesEncoder.encode(
                gatewayAsyncResourceService.getBatchByUpstreamObjectId(normalizeBatchName(batchName), distributedKey.id())
        );
    }

    @PostMapping({"/v1beta/batches/{batchName}:cancel", "/v1beta/batches/batches/{batchName}:cancel"})
    public JsonNode cancelBatch(
            @RequestHeader(value = API_KEY_HEADER, required = false) String headerApiKey,
            @RequestParam(value = "key", required = false) String queryApiKey,
            @PathVariable String batchName) {
        AuthenticatedDistributedKey distributedKey = authenticate(headerApiKey, queryApiKey);
        return geminiBatchesEncoder.encode(
                gatewayAsyncResourceService.cancelBatchByUpstreamObjectId(normalizeBatchName(batchName), distributedKey.id())
        );
    }

    private AuthenticatedDistributedKey authenticate(String headerApiKey, String queryApiKey) {
        String token = StringUtils.hasText(headerApiKey) ? headerApiKey : queryApiKey;
        return distributedKeyAuthenticationService.authenticateRawToken(token);
    }

    private String normalizeFileName(String fileName) {
        return fileName.startsWith("files/") ? fileName : "files/" + fileName;
    }

    private String normalizeBatchName(String batchName) {
        return batchName.startsWith("batches/") ? batchName : "batches/" + batchName;
    }
}
