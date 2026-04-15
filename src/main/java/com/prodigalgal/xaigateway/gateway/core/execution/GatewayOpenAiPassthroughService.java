package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.admin.application.ErrorRuleService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.error.ErrorRuleMatchContext;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    private static final String DEFAULT_AZURE_API_VERSION = "2024-10-21";

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final AccountSelectionService accountSelectionService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final ErrorRuleService errorRuleService;
    private final WebClient.Builder webClientBuilder;

    public GatewayOpenAiPassthroughService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            ErrorRuleService errorRuleService,
            WebClient.Builder webClientBuilder) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.accountSelectionService = accountSelectionService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.errorRuleService = errorRuleService;
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

            return executePreparedJson(context.selectionResult(), context.credential(), context.client(), context.upstreamPath(), context.requestPath(), upstreamPayload);
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

            return executePreparedBinary(context.selectionResult(), context.credential(), context.client(), context.upstreamPath(), context.requestPath(), upstreamPayload);
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
        ObjectNode routePayload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        routePayload.put("model", requestedModel);
        formFields.forEach(routePayload::put);
        RouteExecutionContext context = prepareExecution(distributedKeyPrefix, requestPath, requestedModel, routePayload);

        return buildMultipartBody(formFields, files, Map.of())
                .flatMap(body -> executePreparedMultipart(
                        context.selectionResult(),
                        context.credential(),
                        context.client(),
                        context.upstreamPath(),
                        context.requestPath(),
                        body
                ))
                .doOnError(error -> gatewayRouteSelectionService.invalidateSelection(context.selectionResult()))
                .doFinally(signalType -> distributedKeyGovernanceService.releaseConcurrency(context.selectionResult().governanceReservationKey()));
    }

    ResponseEntity<JsonNode> executePreparedJson(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            WebClient client,
            String upstreamPath,
            String requestPath,
            JsonNode upstreamPayload) {
        RouteExecutionContext context = new RouteExecutionContext(selectionResult, credential, client, upstreamPath, requestPath);
        ResponseEntity<JsonNode> upstreamResponse = client.post()
                .uri(upstreamPath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(upstreamPayload)
                .exchangeToMono(response -> response.toEntity(JsonNode.class))
                .block();
        return finalizeJsonResponse(context, upstreamResponse);
    }

    ResponseEntity<byte[]> executePreparedBinary(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            WebClient client,
            String upstreamPath,
            String requestPath,
            JsonNode upstreamPayload) {
        RouteExecutionContext context = new RouteExecutionContext(selectionResult, credential, client, upstreamPath, requestPath);
        ResponseEntity<byte[]> upstreamResponse = client.post()
                .uri(upstreamPath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(upstreamPayload)
                .exchangeToMono(response -> response.toEntity(byte[].class))
                .block();
        return finalizeBinaryResponse(context, upstreamResponse);
    }

    Mono<ResponseEntity<JsonNode>> executePreparedMultipart(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            WebClient client,
            String upstreamPath,
            String requestPath,
            MultiValueMap<String, HttpEntity<?>> body) {
        RouteExecutionContext context = new RouteExecutionContext(selectionResult, credential, client, upstreamPath, requestPath);
        return client.post()
                .uri(upstreamPath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .exchangeToMono(response -> response.toEntity(JsonNode.class))
                .map(response -> finalizeJsonResponse(context, response));
    }

    Mono<MultiValueMap<String, HttpEntity<?>>> prepareMultipartBody(
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        return buildMultipartBody(formFields, files, Map.of());
    }

    Mono<MultiValueMap<String, HttpEntity<?>>> prepareMultipartBody(
            Map<String, String> formFields,
            Map<String, FilePart> files,
            Map<String, GatewayFileContent> gatewayFiles) {
        return buildMultipartBody(formFields, files, gatewayFiles);
    }

    CatalogSiteRequest buildPreparedSiteRequest(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            String apiKey,
            String requestPath) {
        return buildSiteRequest(
                selectionResult,
                credential,
                new ResolvedCredentialMaterial(
                        credential.getId(),
                        credential.getSiteProfileId(),
                        CredentialAuthKind.API_KEY,
                        apiKey,
                        null,
                        Map.of(),
                        null,
                        "legacy"
                ),
                requestPath
        );
    }

    private Mono<MultiValueMap<String, HttpEntity<?>>> buildMultipartBody(
            Map<String, String> formFields,
            Map<String, FilePart> files,
            Map<String, GatewayFileContent> gatewayFiles) {
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
        return current.map(existing -> {
            gatewayFiles.forEach((fieldName, fileContent) -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(fileContent.mimeType() == null || fileContent.mimeType().isBlank()
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(fileContent.mimeType()));
                existing.add(fieldName, new HttpEntity<>(new ByteArrayResource(fileContent.bytes()) {
                    @Override
                    public String getFilename() {
                        return fileContent.metadata().filename();
                    }
                }, headers));
            });
            return existing;
        });
    }

    private ResponseEntity<JsonNode> finalizeJsonResponse(
            RouteExecutionContext context,
            ResponseEntity<JsonNode> upstreamResponse) {
        if (upstreamResponse == null) {
            throw new IllegalStateException("上游响应为空。");
        }
        recordRouteOutcome(context.selectionResult(), upstreamResponse.getStatusCode().value());
        if (!upstreamResponse.getStatusCode().is2xxSuccessful()) {
            maybeRaiseRule(context, upstreamResponse.getStatusCode().value(), upstreamResponse.getBody());
        }
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

        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(selectionResult, credential);
        CatalogSiteRequest siteRequest = buildSiteRequest(selectionResult, credential, credentialMaterial, requestPath);
        return new RouteExecutionContext(selectionResult, credential, siteRequest.client(), siteRequest.path(), requestPath);
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

    private CatalogSiteRequest buildSiteRequest(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            ResolvedCredentialMaterial credentialMaterial,
            String requestPath) {
        var candidate = selectionResult.selectedCandidate().candidate();
        WebClient.Builder builder = webClientBuilder.clone()
                .baseUrl(normalizeBaseUrl(credential.getBaseUrl()));
        String path = resolvePath(credential.getBaseUrl(), requestPath);
        if (candidate.pathStrategy() == PathStrategy.AZURE_OPENAI_DEPLOYMENT) {
            builder.defaultHeader("api-key", credentialMaterial.secret());
            path = resolveAzurePath(requestPath, selectionResult.resolvedModelKey());
        } else if (candidate.authStrategy() == AuthStrategy.BEARER) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credentialMaterial.secret());
        } else if (candidate.authStrategy() == AuthStrategy.API_KEY_HEADER) {
            builder.defaultHeader("x-api-key", credentialMaterial.secret());
        } else if (candidate.authStrategy() == AuthStrategy.API_KEY_QUERY) {
            path = appendQuery(path, "key", credentialMaterial.secret());
        } else if (candidate.authStrategy() == AuthStrategy.AZURE_API_KEY) {
            builder.defaultHeader("api-key", credentialMaterial.secret());
        }
        return new CatalogSiteRequest(builder.build(), path);
    }

    private String resolveAzurePath(String requestPath, String resolvedModelKey) {
        if ("/v1/embeddings".equals(requestPath)) {
            return "/openai/deployments/" + encodePath(resolvedModelKey) + "/embeddings?api-version=" + DEFAULT_AZURE_API_VERSION;
        }
        return requestPath;
    }

    private String appendQuery(String path, String key, String value) {
        String separator = path.contains("?") ? "&" : "?";
        return path + separator + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode rewriteModelField(JsonNode responseBody, String publicModel) {
        if (responseBody instanceof ObjectNode objectNode && publicModel != null && !publicModel.isBlank()) {
            objectNode.put("model", publicModel);
        }
        return responseBody;
    }

    private void maybeRaiseRule(RouteExecutionContext context, int status, JsonNode body) {
        String errorCode = body == null ? null : body.path("error").path("code").asText(null);
        String message = body == null ? null : body.path("error").path("message").asText(null);
        errorRuleService.evaluate(new ErrorRuleMatchContext(
                context.selectionResult().selectedCandidate().candidate().providerType().name(),
                "openai",
                context.selectionResult().requestedModel(),
                context.requestPath(),
                status,
                errorCode,
                "UPSTREAM",
                message
        )).ifPresent(exception -> { throw exception; });
    }

    private record RouteExecutionContext(
            RouteSelectionResult selectionResult,
            UpstreamCredentialEntity credential,
            WebClient client,
            String upstreamPath,
            String requestPath
    ) {
    }

    record CatalogSiteRequest(
            WebClient client,
            String path
    ) {
    }
}
