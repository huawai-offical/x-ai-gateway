package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1/images")
public class OpenAiImagesController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService;

    public OpenAiImagesController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayOpenAiPassthroughService gatewayOpenAiPassthroughService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayOpenAiPassthroughService = gatewayOpenAiPassthroughService;
    }

    @PostMapping("/generations")
    public ResponseEntity<JsonNode> createGeneration(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayOpenAiPassthroughService.executeJson(
                distributedKey.keyPrefix(),
                "/v1/images/generations",
                requestBody,
                "gpt-image-1"
        );
    }

    @PostMapping(value = "/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<JsonNode>> createEdit(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestPart("image") FilePart image,
            @RequestPart("prompt") String prompt,
            @RequestPart(value = "model", required = false) String model,
            @RequestPart(value = "mask", required = false) FilePart mask,
            @RequestPart(value = "size", required = false) String size,
            @RequestPart(value = "quality", required = false) String quality) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("prompt", prompt);
        formFields.put("model", model == null || model.isBlank() ? "gpt-image-1" : model);
        putIfPresent(formFields, "size", size);
        putIfPresent(formFields, "quality", quality);

        Map<String, FilePart> fileParts = new LinkedHashMap<>();
        fileParts.put("image", image);
        if (mask != null) {
            fileParts.put("mask", mask);
        }

        return gatewayOpenAiPassthroughService.executeMultipartJson(
                distributedKey.keyPrefix(),
                "/v1/images/edits",
                formFields.get("model"),
                formFields,
                fileParts
        );
    }

    @PostMapping(value = "/variations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<JsonNode>> createVariation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestPart("image") FilePart image,
            @RequestPart(value = "model", required = false) String model,
            @RequestPart(value = "size", required = false) String size) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("model", model == null || model.isBlank() ? "gpt-image-1" : model);
        putIfPresent(formFields, "size", size);
        return gatewayOpenAiPassthroughService.executeMultipartJson(
                distributedKey.keyPrefix(),
                "/v1/images/variations",
                formFields.get("model"),
                formFields,
                Map.of("image", image)
        );
    }

    private void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }
}
