package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.databind.JsonNode;
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
@RequestMapping("/v1/audio")
public class OpenAiAudioController {

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final GatewayResourceExecutionService gatewayResourceExecutionService;

    public OpenAiAudioController(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            GatewayResourceExecutionService gatewayResourceExecutionService) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.gatewayResourceExecutionService = gatewayResourceExecutionService;
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<JsonNode>> createTranscription(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestPart("file") FilePart file,
            @RequestPart("model") String model,
            @RequestPart(value = "language", required = false) String language,
            @RequestPart(value = "prompt", required = false) String prompt,
            @RequestPart(value = "response_format", required = false) String responseFormat,
            @RequestPart(value = "temperature", required = false) String temperature) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("model", model);
        putIfPresent(formFields, "language", language);
        putIfPresent(formFields, "prompt", prompt);
        putIfPresent(formFields, "response_format", responseFormat);
        putIfPresent(formFields, "temperature", temperature);
        return gatewayResourceExecutionService.executeMultipartJson(
                distributedKey.keyPrefix(),
                "/v1/audio/transcriptions",
                model,
                formFields,
                Map.of("file", file)
        );
    }

    @PostMapping(value = "/translations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<JsonNode>> createTranslation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestPart("file") FilePart file,
            @RequestPart("model") String model,
            @RequestPart(value = "prompt", required = false) String prompt,
            @RequestPart(value = "response_format", required = false) String responseFormat,
            @RequestPart(value = "temperature", required = false) String temperature) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("model", model);
        putIfPresent(formFields, "prompt", prompt);
        putIfPresent(formFields, "response_format", responseFormat);
        putIfPresent(formFields, "temperature", temperature);
        return gatewayResourceExecutionService.executeMultipartJson(
                distributedKey.keyPrefix(),
                "/v1/audio/translations",
                model,
                formFields,
                Map.of("file", file)
        );
    }

    @PostMapping("/speech")
    public ResponseEntity<byte[]> createSpeech(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody JsonNode requestBody) {
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(authorization, null, null, null);
        return gatewayResourceExecutionService.executeBinaryJson(
                distributedKey.keyPrefix(),
                "/v1/audio/speech",
                requestBody,
                "gpt-4o-mini-tts"
        );
    }

    private void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }
}
