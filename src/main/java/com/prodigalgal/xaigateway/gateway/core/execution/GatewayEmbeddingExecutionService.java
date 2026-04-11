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
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Transactional
public class GatewayEmbeddingExecutionService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final AccountSelectionService accountSelectionService;
    private final WebClient.Builder webClientBuilder;

    public GatewayEmbeddingExecutionService(
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

    public ResponseEntity<JsonNode> executeOpenAiEmbeddings(String distributedKeyPrefix, JsonNode requestBody) {
        ObjectNode payload = requireObjectPayload(requestBody);
        String requestedModel = readRequiredModel(payload);

        String requestId = gatewayObservabilityService.nextRequestId();
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                distributedKeyPrefix,
                "openai",
                "/v1/embeddings",
                requestedModel,
                payload,
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        gatewayObservabilityService.recordRouteDecision(requestId, selectionResult);

        if (!selectionResult.selectedCandidate().candidate().supportsEmbeddings()) {
            throw new IllegalArgumentException("当前模型不支持 embeddings。");
        }

        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        String apiKey = accountSelectionService.resolveActiveAccount(
                        selectionResult.distributedKeyId(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.clientFamily(),
                        300)
                .map(account -> credentialCryptoService.decrypt(account.getAccessTokenCiphertext()))
                .orElseGet(() -> credentialCryptoService.decrypt(credential.getApiKeyCiphertext()));

        try {
            ObjectNode upstreamPayload = payload.deepCopy();
            upstreamPayload.put("model", selectionResult.resolvedModelKey());

            WebClient client = webClientBuilder.clone()
                    .baseUrl(normalizeBaseUrl(credential.getBaseUrl()))
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .build();

            ResponseEntity<JsonNode> upstreamResponse = client.post()
                    .uri(resolveEmbeddingsPath(credential.getBaseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(upstreamPayload)
                    .exchangeToMono(response -> response.toEntity(JsonNode.class))
                    .block();

            if (upstreamResponse == null) {
                throw new IllegalStateException("上游 embeddings 响应为空。");
            }

            if (upstreamResponse.getStatusCode().is2xxSuccessful()) {
                gatewayRouteSelectionService.recordSuccessfulSelection(selectionResult);
            } else if (upstreamResponse.getStatusCode().is5xxServerError() || upstreamResponse.getStatusCode().value() == 429) {
                gatewayRouteSelectionService.invalidateSelection(selectionResult);
            }

            JsonNode responseBody = rewriteModelField(upstreamResponse.getBody(), selectionResult.publicModel());
            MediaType contentType = upstreamResponse.getHeaders().getContentType() == null
                    ? MediaType.APPLICATION_JSON
                    : upstreamResponse.getHeaders().getContentType();
            return ResponseEntity.status(upstreamResponse.getStatusCode())
                    .contentType(contentType)
                    .body(responseBody);
        } catch (RuntimeException exception) {
            gatewayRouteSelectionService.invalidateSelection(selectionResult);
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey());
        }
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("embeddings 请求体必须是 JSON object。");
        }
        return (ObjectNode) requestBody;
    }

    private String readRequiredModel(ObjectNode payload) {
        String model = payload.path("model").asText(null);
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("embeddings 请求缺少 model。");
        }
        return model;
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

    private String resolveEmbeddingsPath(String baseUrl) {
        return normalizeBaseUrl(baseUrl).endsWith("/v1") ? "/embeddings" : "/v1/embeddings";
    }

    private JsonNode rewriteModelField(JsonNode responseBody, String publicModel) {
        if (responseBody instanceof ObjectNode objectNode && publicModel != null && !publicModel.isBlank()) {
            objectNode.put("model", publicModel);
        }
        return responseBody;
    }
}
