package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/v1/vector_stores")
public class OpenAiVectorStoresController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayAsyncResourceService gatewayAsyncResourceService;

    public OpenAiVectorStoresController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayAsyncResourceService gatewayAsyncResourceService) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
    }

    @PostMapping
    public JsonNode createVectorStore(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.createVectorStore(distributedKey.id(), requestBody);
    }

    @GetMapping
    public JsonNode listVectorStores(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String order) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.listVectorStores(distributedKey.id(), after, limit, order);
    }

    @GetMapping("/{vectorStoreId}")
    public JsonNode getVectorStore(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getVectorStore(vectorStoreId, distributedKey.id());
    }

    @PostMapping("/{vectorStoreId}")
    public JsonNode updateVectorStore(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.updateVectorStore(vectorStoreId, distributedKey.id(), requestBody);
    }

    @PostMapping("/{vectorStoreId}/search")
    public JsonNode searchVectorStore(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.searchVectorStore(vectorStoreId, distributedKey.id(), requestBody);
    }

    @DeleteMapping("/{vectorStoreId}")
    public JsonNode deleteVectorStore(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.deleteVectorStore(vectorStoreId, distributedKey.id());
    }

    @PostMapping("/{vectorStoreId}/files")
    public JsonNode createVectorStoreFile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.createVectorStoreFile(vectorStoreId, distributedKey.id(), requestBody);
    }

    @GetMapping("/{vectorStoreId}/files")
    public JsonNode listVectorStoreFiles(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String filter) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.listVectorStoreFiles(
                vectorStoreId,
                distributedKey.id(),
                after,
                limit,
                order,
                filter
        );
    }

    @GetMapping("/{vectorStoreId}/files/{fileId}")
    public JsonNode getVectorStoreFile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @PathVariable String fileId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getVectorStoreFile(vectorStoreId, fileId, distributedKey.id());
    }

    @GetMapping("/{vectorStoreId}/files/{fileId}/content")
    public JsonNode getVectorStoreFileContent(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @PathVariable String fileId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getVectorStoreFileContent(vectorStoreId, fileId, distributedKey.id());
    }

    @DeleteMapping("/{vectorStoreId}/files/{fileId}")
    public JsonNode deleteVectorStoreFile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @PathVariable String fileId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.deleteVectorStoreFile(vectorStoreId, fileId, distributedKey.id());
    }

    @PostMapping("/{vectorStoreId}/file_batches")
    public JsonNode createVectorStoreFileBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @RequestBody(required = false) JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.createVectorStoreFileBatch(vectorStoreId, distributedKey.id(), requestBody);
    }

    @GetMapping("/{vectorStoreId}/file_batches/{batchId}")
    public JsonNode getVectorStoreFileBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @PathVariable String batchId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.getVectorStoreFileBatch(vectorStoreId, batchId, distributedKey.id());
    }

    @PostMapping("/{vectorStoreId}/file_batches/{batchId}/cancel")
    public JsonNode cancelVectorStoreFileBatch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @PathVariable String batchId) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.cancelVectorStoreFileBatch(vectorStoreId, batchId, distributedKey.id());
    }

    @GetMapping("/{vectorStoreId}/file_batches/{batchId}/files")
    public JsonNode listVectorStoreFileBatchFiles(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String vectorStoreId,
            @PathVariable String batchId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String filter) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);
        return gatewayAsyncResourceService.listVectorStoreFileBatchFiles(
                vectorStoreId,
                batchId,
                distributedKey.id(),
                after,
                limit,
                order,
                filter
        );
    }
}
