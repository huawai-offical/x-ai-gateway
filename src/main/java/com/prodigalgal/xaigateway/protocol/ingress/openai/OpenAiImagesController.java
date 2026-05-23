package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionService;
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
    private final GatewayResourceExecutionService gatewayResourceExecutionService;

    public OpenAiImagesController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayResourceExecutionService gatewayResourceExecutionService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
    }

    @PostMapping("/generations")
    public ResponseEntity<JsonNode> createGeneration(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeJson(
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
            @RequestPart(value = "mask", required = false) FilePart mask,
            @RequestPart("prompt") String prompt,
            @RequestPart(value = "model", required = false) String model,
            @RequestPart(value = "background", required = false) String background,
            @RequestPart(value = "input_fidelity", required = false) String inputFidelity,
            @RequestPart(value = "n", required = false) String n,
            @RequestPart(value = "output_compression", required = false) String outputCompression,
            @RequestPart(value = "output_format", required = false) String outputFormat,
            @RequestPart(value = "quality", required = false) String quality,
            @RequestPart(value = "response_format", required = false) String responseFormat,
            @RequestPart(value = "size", required = false) String size,
            @RequestPart(value = "user", required = false) String user) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        String resolvedModel = model == null || model.isBlank() ? "gpt-image-1" : model;
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("model", resolvedModel);
        formFields.put("prompt", prompt);
        putIfPresent(formFields, "background", background);
        putIfPresent(formFields, "input_fidelity", inputFidelity);
        putIfPresent(formFields, "n", n);
        putIfPresent(formFields, "output_compression", outputCompression);
        putIfPresent(formFields, "output_format", outputFormat);
        putIfPresent(formFields, "quality", quality);
        putIfPresent(formFields, "response_format", responseFormat);
        putIfPresent(formFields, "size", size);
        putIfPresent(formFields, "user", user);
        Map<String, FilePart> files = new LinkedHashMap<>();
        files.put("image", image);
        if (mask != null) {
            files.put("mask", mask);
        }
        return gatewayResourceExecutionService.executeMultipartJson(
                distributedKey.keyPrefix(),
                "/v1/images/edits",
                resolvedModel,
                formFields,
                files
        );
    }

    @PostMapping(value = "/variations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<JsonNode>> createVariation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestPart("image") FilePart image,
            @RequestPart(value = "model", required = false) String model,
            @RequestPart(value = "n", required = false) String n,
            @RequestPart(value = "response_format", required = false) String responseFormat,
            @RequestPart(value = "size", required = false) String size,
            @RequestPart(value = "user", required = false) String user) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        String resolvedModel = model == null || model.isBlank() ? "dall-e-2" : model;
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("model", resolvedModel);
        putIfPresent(formFields, "n", n);
        putIfPresent(formFields, "response_format", responseFormat);
        putIfPresent(formFields, "size", size);
        putIfPresent(formFields, "user", user);
        return gatewayResourceExecutionService.executeMultipartJson(
                distributedKey.keyPrefix(),
                "/v1/images/variations",
                resolvedModel,
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
