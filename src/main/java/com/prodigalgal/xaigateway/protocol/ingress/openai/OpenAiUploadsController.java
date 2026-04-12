package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
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
    private final GatewayAsyncResourceService gatewayAsyncResourceService;

    public OpenAiUploadsController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayAsyncResourceService gatewayAsyncResourceService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
    }

    @PostMapping
    public JsonNode createUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.createUpload(distributedKey.id(), requestBody);
    }

    @GetMapping("/{uploadId}")
    public JsonNode getUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.getUpload(uploadId, distributedKey.id());
    }

    @PostMapping(path = "/{uploadId}/parts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<JsonNode> addPart(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId,
            @RequestPart("data") FilePart data) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.addUploadPart(uploadId, distributedKey.id(), data);
    }

    @PostMapping("/{uploadId}/complete")
    public JsonNode completeUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.completeUpload(uploadId, distributedKey.id());
    }

    @PostMapping("/{uploadId}/cancel")
    public JsonNode cancelUpload(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayAsyncResourceService.cancelUpload(uploadId, distributedKey.id());
    }
}
