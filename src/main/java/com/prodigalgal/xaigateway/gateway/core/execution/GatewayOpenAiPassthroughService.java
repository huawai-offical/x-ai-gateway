package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class GatewayOpenAiPassthroughService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final AccountSelectionService accountSelectionService;
    private final WebClient.Builder webClientBuilder;

    public GatewayOpenAiPassthroughService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            AccountSelectionService accountSelectionService,
            WebClient.Builder webClientBuilder) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.accountSelectionService = accountSelectionService;
        this.webClientBuilder = webClientBuilder;
    }

    public ResponseEntity<JsonNode> executeJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody);
        String requestedModel = resolveRequestedModel(payload, defaultModel);
        RouteExecutionContext context = prepareExecution(distributedKeyPrefix, requestPath, requestedModel, payload);

        try {
            ObjectNode upstreamPayload = payload.deepCopy();
            upstreamPayload.put("model", context.selectionResult().resolvedModelKey());

            ResponseEntity<JsonNode> upstreamResponse = context.client().post()
                    .uri(resolvePath(context.credential().getBaseUrl(), requestPath))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(upstreamPayload)
                    .exchangeToMono(response -> response.toEntity(JsonNode.class))
                    .block();

            return finalizeJsonResponse(context, upstreamResponse);
        } catch (RuntimeException exception) {
            gatewayRouteSelectionService.invalidateSelection(context.selectionResult());
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(context.selectionResult().governanceReservationKey());
        }
    }

    public ResponseEntity<byte[]> executeBinaryJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody);
        String requestedModel = resolveRequestedModel(payload, defaultModel);
        RouteExecutionContext context = prepareExecution(distributedKeyPrefix, requestPath, requestedModel, payload);

        try {
            ObjectNode upstreamPayload = payload.deepCopy();
            upstreamPayload.put("model", context.selectionResult().resolvedModelKey());

            ResponseEntity<byte[]> upstreamResponse = context.client().post()
                    .uri(resolvePath(context.credential().getBaseUrl(), requestPath))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(upstreamPayload)
                    .exchangeToMono(response -> response.toEntity(byte[].class))
                    .block();

            return finalizeBinaryResponse(context, upstreamResponse);
        } catch (RuntimeException exception) {
            gatewayRouteSelectionService.invalidateSelection(context.selectionResult());
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(context.selectionResult().governanceReservationKey());
        }
    }

    public Mono<ResponseEntity<JsonNode>> executeMultipartJson(
            String distributedKeyPrefix,
            String requestPath,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        ObjectNode routePayload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        routePayload.put("model", requestedModel);
        formFields.forEach(routePayload::put);
        RouteExecutionContext context = prepareExecution(distributedKeyPrefix, requestPath, requestedModel, routePayload);

        return buildMultipartBody(formFields, files)
                .flatMap(body -> context.client().post()
                        .uri(resolvePath(context.credential().getBaseUrl(), requestPath))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(body))
                        .exchangeToMono(response -> response.toEntity(JsonNode.class)))
                .map(response -> finalizeJsonResponse(context, response))
                .doOnError(error -> gatewayRouteSelectionService.invalidateSelection(context.selectionResult()))
                .doFinally(signalType -> distributedKeyGovernanceService.releaseConcurrency(context.selectionResult().governanceReservationKey()));
    }

    private Mono<MultiValueMap<String, HttpEntity<?>>> buildMultipartBody(
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        formFields.forEach((name, value) -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            body.add(name, new HttpEntity<>(value, headers));
        });

        Mono<MultiValueMap<String, HttpEntity<?>>> current = Mono.just(body);
        for (Map.Entry<String, FilePart> entry : files.entrySet()) {
            String fieldName = entry.getKey();
            FilePart filePart = entry.getValue();
            current = current.flatMap(existing -> DataBufferUtils.join(filePart.content())
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(filePart.headers().getContentType() == null
                                ? MediaType.APPLICATION_OCTET_STREAM
                                : filePart.headers().getContentType());
                        existing.add(fieldName, new HttpEntity<>(new ByteArrayResource(bytes) {
                            @Override
                            public String getFilename() {
                                return filePart.filename();
                            }
                        }, headers));
                        return existing;
                    }));
        }
        return current;
    }

    private ResponseEntity<JsonNode> finalizeJsonResponse(
            RouteExecutionContext context,
            ResponseEntity<JsonNode> upstreamResponse) {
        if (upstreamResponse == null) {
            throw new IllegalStateException("上游响应为空。");
        }
        recordRouteOutcome(context.selectionResult(), upstreamResponse.getStatusCode().value());
        JsonNode body = rewriteModelField(upstreamResponse.getBody(), context.selectionResult().publicModel());
        MediaType contentType = upstreamResponse.getHeaders().getContentType() == null
                ? MediaType.APPLICATION_JSON
                : upstreamResponse.getHeaders().getContentType();
        return ResponseEntity.status(upstreamResponse.getStatusCode())
                .contentType(contentType)
                .body(body);
    }

    private ResponseEntity<byte[]> finalizeBinaryResponse(
            RouteExecutionContext context,
            ResponseEntity<byte[]> upstreamResponse) {
        if (upstreamResponse == null) {
            throw new IllegalStateException("上游响应为空。");
        }
        recordRouteOutcome(context.selectionResult(), upstreamResponse.getStatusCode().value());
        MediaType contentType = upstreamResponse.getHeaders().getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : upstreamResponse.getHeaders().getContentType();
        return ResponseEntity.status(upstreamResponse.getStatusCode())
                .contentType(contentType)
                .headers(upstreamResponse.getHeaders())
                .body(upstreamResponse.getBody());
    }

    private void recordRouteOutcome(RouteSelectionResult selectionResult, int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            gatewayRouteSelectionService.recordSuccessfulSelection(selectionResult);
            return;
        }
        if (statusCode == 429 || statusCode >= 500) {
            gatewayRouteSelectionService.invalidateSelection(selectionResult);
        }
    }

    private RouteExecutionContext prepareExecution(
            String distributedKeyPrefix,
            String requestPath,
            String requestedModel,
            Object requestBody) {
        String requestId = gatewayObservabilityService.nextRequestId();
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                distributedKeyPrefix,
                "openai",
                requestPath,
                requestedModel,
                requestBody,
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        gatewayObservabilityService.recordRouteDecision(requestId, selectionResult);

        ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
        if (providerType != ProviderType.OPENAI_DIRECT && providerType != ProviderType.OPENAI_COMPATIBLE) {
            throw new IllegalArgumentException("当前资源协议首版仅支持 OpenAI / OpenAI-compatible 上游。");
        }

        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        String apiKey = accountSelectionService.resolveActiveAccount(
                        selectionResult.distributedKeyId(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.clientFamily(),
                        300)
                .map(account -> credentialCryptoService.decrypt(account.getAccessTokenCiphertext()))
                .orElseGet(() -> credentialCryptoService.decrypt(credential.getApiKeyCiphertext()));
        WebClient client = webClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(credential.getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        return new RouteExecutionContext(selectionResult, credential, client);
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        return (ObjectNode) requestBody;
    }

    private String resolveRequestedModel(ObjectNode payload, String defaultModel) {
        String model = payload.path("model").asText(null);
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        if (defaultModel != null && !defaultModel.isBlank()) {
            payload.put("model", defaultModel);
            return defaultModel;
        }
        throw new IllegalArgumentException("请求缺少 model。");
    }

    private UpstreamCredentialEntity getRequiredCredential(Long credentialId) {
        Optional<UpstreamCredentialEntity> credential = upstreamCredentialRepository.findById(credentialId);
        if (credential.isEmpty() || credential.get().isDeleted()) {
            throw new IllegalArgumentException("未找到对应的上游凭证。");
        }
        return credential.get();
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }

    private String resolvePath(String baseUrl, String requestPath) {
        String normalizedPath = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
        if (normalizeBaseUrl(baseUrl).endsWith("/v1") && normalizedPath.startsWith("/v1/")) {
            return normalizedPath.substring(3);
        }
        return normalizedPath;
    }

    private JsonNode rewriteModelField(JsonNode responseBody, String publicModel) {
        if (responseBody instanceof ObjectNode objectNode && publicModel != null && !publicModel.isBlank()) {
            objectNode.put("model", publicModel);
        }
        return responseBody;
    }

    private record RouteExecutionContext(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            WebClient client
    ) {
    }
}
