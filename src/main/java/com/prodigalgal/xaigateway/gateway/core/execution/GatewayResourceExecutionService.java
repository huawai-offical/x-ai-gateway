package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class GatewayResourceExecutionService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final AccountSelectionService accountSelectionService;
    private final List<GatewayResourceExecutor> gatewayResourceExecutors;

    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            AccountSelectionService accountSelectionService,
            List<GatewayResourceExecutor> gatewayResourceExecutors) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.accountSelectionService = accountSelectionService;
        this.gatewayResourceExecutors = gatewayResourceExecutors;
    }

    public ResponseEntity<JsonNode> executeJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        RouteSelectionResult selectionResult = select(distributedKeyPrefix, requestPath, payload.path("model").asText(), payload);
        GatewayResourceExecutionContext context = prepareContext(selectionResult, requestPath);
        try {
            ResponseEntity<JsonNode> response = resolveExecutor(context).executeJson(context, payload, defaultModel);
            recordRouteOutcome(selectionResult, response.getStatusCode().value());
            return response;
        } catch (RuntimeException exception) {
            gatewayRouteSelectionService.invalidateSelection(selectionResult);
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey());
        }
    }

    public ResponseEntity<JsonNode> executeEmbeddings(
            String distributedKeyPrefix,
            JsonNode requestBody,
            String defaultModel) {
        return executeJson(distributedKeyPrefix, "/v1/embeddings", requestBody, defaultModel);
    }

    public ResponseEntity<byte[]> executeBinaryJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        RouteSelectionResult selectionResult = select(distributedKeyPrefix, requestPath, payload.path("model").asText(), payload);
        GatewayResourceExecutionContext context = prepareContext(selectionResult, requestPath);
        try {
            ResponseEntity<byte[]> response = resolveExecutor(context).executeBinary(context, payload, defaultModel);
            recordRouteOutcome(selectionResult, response.getStatusCode().value());
            return response;
        } catch (RuntimeException exception) {
            gatewayRouteSelectionService.invalidateSelection(selectionResult);
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey());
        }
    }

    public Mono<ResponseEntity<JsonNode>> executeMultipartJson(
            String distributedKeyPrefix,
            String requestPath,
            String requestedModel,
            Map<String, String> formFields,
            Map<String, FilePart> files) {
        ObjectNode routePayload = JsonNodeFactory.instance.objectNode();
        routePayload.put("model", requestedModel);
        formFields.forEach(routePayload::put);
        RouteSelectionResult selectionResult = select(distributedKeyPrefix, requestPath, requestedModel, routePayload);
        GatewayResourceExecutionContext context = prepareContext(selectionResult, requestPath);
        return resolveExecutor(context).executeMultipart(context, requestedModel, formFields, files)
                .doOnNext(response -> recordRouteOutcome(selectionResult, response.getStatusCode().value()))
                .doOnError(error -> gatewayRouteSelectionService.invalidateSelection(selectionResult))
                .doFinally(signalType -> distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey()));
    }

    private RouteSelectionResult select(
            String distributedKeyPrefix,
            String requestPath,
            String requestedModel,
            Object requestBody) {
        return gatewayRouteSelectionService.select(new RouteSelectionRequest(
                distributedKeyPrefix,
                "openai",
                requestPath,
                requestedModel,
                requestBody,
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
    }

    private GatewayResourceExecutionContext prepareContext(RouteSelectionResult selectionResult, String requestPath) {
        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        String apiKey = accountSelectionService.resolveActiveAccount(
                        selectionResult.distributedKeyId(),
                        selectionResult.selectedCandidate().candidate().providerType(),
                        selectionResult.clientFamily(),
                        300)
                .map(account -> credentialCryptoService.decrypt(account.getAccessTokenCiphertext()))
                .orElseGet(() -> credentialCryptoService.decrypt(credential.getApiKeyCiphertext()));
        return new GatewayResourceExecutionContext(selectionResult, credential, apiKey, requestPath);
    }

    private GatewayResourceExecutor resolveExecutor(GatewayResourceExecutionContext context) {
        return gatewayResourceExecutors.stream()
                .filter(executor -> executor.supports(context.requestPath(), context.selectionResult().selectedCandidate().candidate()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前站点不支持该资源执行。"));
    }

    private ObjectNode requireObjectPayload(JsonNode requestBody, String defaultModel) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("请求体必须是 JSON object。");
        }
        ObjectNode payload = (ObjectNode) requestBody;
        if (!payload.hasNonNull("model")) {
            if (defaultModel == null || defaultModel.isBlank()) {
                throw new IllegalArgumentException("请求缺少 model。");
            }
            payload.put("model", defaultModel);
        }
        return payload;
    }

    private UpstreamCredentialEntity getRequiredCredential(Long credentialId) {
        Optional<UpstreamCredentialEntity> credential = upstreamCredentialRepository.findById(credentialId);
        if (credential.isEmpty() || credential.get().isDeleted()) {
            throw new IllegalArgumentException("未找到对应的上游凭证。");
        }
        return credential.get();
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
}
