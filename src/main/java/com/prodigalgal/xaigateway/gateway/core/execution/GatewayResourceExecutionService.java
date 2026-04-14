package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteExecutionAttempt;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;
    private final List<GatewayResourceExecutor> gatewayResourceExecutors;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final GatewayProperties gatewayProperties;

    @Autowired
    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            List<GatewayResourceExecutor> gatewayResourceExecutors,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayProperties gatewayProperties) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.accountSelectionService = accountSelectionService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
        this.gatewayResourceExecutors = gatewayResourceExecutors;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.gatewayProperties = gatewayProperties;
    }

    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            AccountSelectionService accountSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            List<GatewayResourceExecutor> gatewayResourceExecutors,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayProperties gatewayProperties) {
        this(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                accountSelectionService,
                new CredentialMaterialResolver(accountSelectionService, credentialCryptoService, new tools.jackson.databind.ObjectMapper()),
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                gatewayResourceExecutors,
                gatewayObservabilityService,
                gatewayProperties
        );
    }

    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            AccountSelectionService accountSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            List<GatewayResourceExecutor> gatewayResourceExecutors) {
        this(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                accountSelectionService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                gatewayResourceExecutors,
                new GatewayObservabilityService(null, null, null, new tools.jackson.databind.ObjectMapper()) {
                    @Override
                    public String nextRequestId() {
                        return "test-request";
                    }

                    @Override
                    public void recordRouteDecision(String requestId, RouteSelectionResult selectionResult) {
                    }
                },
                new GatewayProperties()
        );
    }

    public ResponseEntity<JsonNode> executeJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        ObjectNode payload = requireObjectPayload(requestBody, defaultModel);
        RouteSelectionResult selectionResult = select(distributedKeyPrefix, requestPath, payload.path("model").asText(), payload);
        String requestId = gatewayObservabilityService.nextRequestId();
        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
            RuntimeException lastException = null;
            for (int index = 0; index < maxAttempts; index++) {
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, selectionResult.candidates().get(index), attempts);
                GatewayResourceExecutionContext context = prepareContext(candidateSelection, requestPath, payload);
                try {
                    ResponseEntity<JsonNode> response = resolveExecutor(context).executeJson(context, payload, defaultModel);
                    if (shouldFallback(response.getStatusCode().value(), response.getBody())) {
                        attempts.add(new RouteExecutionAttempt(
                                index + 1,
                                candidateSelection.selectedCandidate().candidate().credentialId(),
                                candidateSelection.selectedCandidate().candidate().providerType().name(),
                                "FAILED_BEFORE_FIRST_BYTE",
                                "status=" + response.getStatusCode().value()
                        ));
                        gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                        gatewayRouteSelectionService.markCredentialCooldown(candidateSelection.selectedCandidate().candidate().credentialId(), "status=" + response.getStatusCode().value());
                        if (index == maxAttempts - 1) {
                            gatewayObservabilityService.recordRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)));
                            return response;
                        }
                        continue;
                    }
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "SUCCEEDED",
                            candidateSelection.selectionSource().name()
                    ));
                    RouteSelectionResult finalSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                    gatewayRouteSelectionService.recordSuccessfulSelection(finalSelection);
                    gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                    return response;
                } catch (RuntimeException exception) {
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            exception.getMessage()
                    ));
                    gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                    gatewayRouteSelectionService.markCredentialCooldown(candidateSelection.selectedCandidate().candidate().credentialId(), exception.getMessage());
                    lastException = exception;
                    if (!shouldFallback(exception) || index == maxAttempts - 1) {
                        gatewayObservabilityService.recordRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)));
                        throw exception;
                    }
                }
            }
            if (lastException != null) {
                throw lastException;
            }
            throw new IllegalStateException("当前资源请求没有可用候选。");
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
        String requestId = gatewayObservabilityService.nextRequestId();
        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
            RuntimeException lastException = null;
            for (int index = 0; index < maxAttempts; index++) {
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, selectionResult.candidates().get(index), attempts);
                GatewayResourceExecutionContext context = prepareContext(candidateSelection, requestPath, payload);
                try {
                    ResponseEntity<byte[]> response = resolveExecutor(context).executeBinary(context, payload, defaultModel);
                    if (shouldFallback(response.getStatusCode().value(), response.getBody())) {
                        attempts.add(new RouteExecutionAttempt(
                                index + 1,
                                candidateSelection.selectedCandidate().candidate().credentialId(),
                                candidateSelection.selectedCandidate().candidate().providerType().name(),
                                "FAILED_BEFORE_FIRST_BYTE",
                                "status=" + response.getStatusCode().value()
                        ));
                        gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                        gatewayRouteSelectionService.markCredentialCooldown(candidateSelection.selectedCandidate().candidate().credentialId(), "status=" + response.getStatusCode().value());
                        if (index == maxAttempts - 1) {
                            gatewayObservabilityService.recordRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)));
                            return response;
                        }
                        continue;
                    }
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "SUCCEEDED",
                            candidateSelection.selectionSource().name()
                    ));
                    RouteSelectionResult finalSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                    gatewayRouteSelectionService.recordSuccessfulSelection(finalSelection);
                    gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                    return response;
                } catch (RuntimeException exception) {
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            exception.getMessage()
                    ));
                    gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                    gatewayRouteSelectionService.markCredentialCooldown(candidateSelection.selectedCandidate().candidate().credentialId(), exception.getMessage());
                    lastException = exception;
                    if (!shouldFallback(exception) || index == maxAttempts - 1) {
                        gatewayObservabilityService.recordRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)));
                        throw exception;
                    }
                }
            }
            if (lastException != null) {
                throw lastException;
            }
            throw new IllegalStateException("当前资源请求没有可用候选。");
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
        String requestId = gatewayObservabilityService.nextRequestId();
        List<RouteExecutionAttempt> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
        return executeMultipartAttempt(
                requestId,
                selectionResult,
                requestPath,
                requestedModel,
                routePayload,
                formFields,
                files,
                0,
                maxAttempts,
                attempts
        )
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

    private Mono<ResponseEntity<JsonNode>> executeMultipartAttempt(
            String requestId,
            RouteSelectionResult baseSelection,
            String requestPath,
            String requestedModel,
            JsonNode routePayload,
            Map<String, String> formFields,
            Map<String, FilePart> files,
            int candidateIndex,
            int maxAttempts,
            List<RouteExecutionAttempt> attempts) {
        RouteSelectionResult candidateSelection = selectionForCandidate(baseSelection, baseSelection.candidates().get(candidateIndex), attempts);
        GatewayResourceExecutionContext context = prepareContext(candidateSelection, requestPath, routePayload);
        return resolveExecutor(context).executeMultipart(context, requestedModel, formFields, files)
                .flatMap(response -> {
                    if (shouldFallback(response.getStatusCode().value(), response.getBody())) {
                        attempts.add(new RouteExecutionAttempt(
                                candidateIndex + 1,
                                candidateSelection.selectedCandidate().candidate().credentialId(),
                                candidateSelection.selectedCandidate().candidate().providerType().name(),
                                "FAILED_BEFORE_FIRST_BYTE",
                                "status=" + response.getStatusCode().value()
                        ));
                        gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                        gatewayRouteSelectionService.markCredentialCooldown(candidateSelection.selectedCandidate().candidate().credentialId(), "status=" + response.getStatusCode().value());
                        if (candidateIndex + 1 < maxAttempts && candidateIndex + 1 < baseSelection.candidates().size()) {
                            return executeMultipartAttempt(
                                    requestId,
                                    baseSelection,
                                    requestPath,
                                    requestedModel,
                                    routePayload,
                                    formFields,
                                    files,
                                    candidateIndex + 1,
                                    maxAttempts,
                                    attempts
                            );
                        }
                        gatewayObservabilityService.recordRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)));
                    } else {
                        attempts.add(new RouteExecutionAttempt(
                                candidateIndex + 1,
                                candidateSelection.selectedCandidate().candidate().credentialId(),
                                candidateSelection.selectedCandidate().candidate().providerType().name(),
                                "SUCCEEDED",
                                candidateSelection.selectionSource().name()
                        ));
                        RouteSelectionResult finalSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        gatewayRouteSelectionService.recordSuccessfulSelection(finalSelection);
                        gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                    }
                    return Mono.just(response);
                })
                .onErrorResume(error -> {
                    attempts.add(new RouteExecutionAttempt(
                            candidateIndex + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            error.getMessage()
                    ));
                    gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                    gatewayRouteSelectionService.markCredentialCooldown(candidateSelection.selectedCandidate().candidate().credentialId(), error.getMessage());
                    if (shouldFallback(error)
                            && candidateIndex + 1 < maxAttempts
                            && candidateIndex + 1 < baseSelection.candidates().size()) {
                        return executeMultipartAttempt(
                                requestId,
                                baseSelection,
                                requestPath,
                                requestedModel,
                                routePayload,
                                formFields,
                                files,
                                candidateIndex + 1,
                                maxAttempts,
                                attempts
                        );
                    }
                    gatewayObservabilityService.recordRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)));
                    return Mono.error(error);
                });
    }

    private GatewayResourceExecutionContext prepareContext(RouteSelectionResult selectionResult, String requestPath, JsonNode requestBody) {
        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(selectionResult, credential);
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(requestPath, null);
        TranslationExecutionPlan executionPlan = translationExecutionPlanCompiler.compileSelected(
                selectionResult,
                requestPath,
                semantics,
                requestBody
        );
        return new GatewayResourceExecutionContext(selectionResult, credential, credentialMaterial, requestPath, executionPlan);
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

    private RouteSelectionResult selectionForCandidate(
            RouteSelectionResult selectionResult,
            RouteCandidateView candidate,
            List<RouteExecutionAttempt> attempts) {
        RouteSelectionSource source = selectionResult.candidateEvaluations().stream()
                .filter(item -> item.candidate().candidate().credentialId().equals(candidate.candidate().credentialId()))
                .map(RouteCandidateEvaluation::selectionSource)
                .findFirst()
                .orElse(RouteSelectionSource.WEIGHTED_HASH);
        return selectionResult.withSelectedCandidate(candidate, source).withAttempts(List.copyOf(attempts));
    }

    private boolean shouldFallback(Throwable throwable) {
        if (throwable instanceof com.prodigalgal.xaigateway.gateway.core.auth.GatewayUnauthorizedException) {
            return false;
        }
        if (throwable instanceof IllegalArgumentException) {
            return false;
        }
        if (throwable instanceof com.prodigalgal.xaigateway.gateway.core.error.GatewayRuleMatchedException matched) {
            return matched.getStatus() == 429 || matched.getStatus() >= 500;
        }
        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException responseException) {
            return responseException.getStatusCode().value() == 429 || responseException.getStatusCode().is5xxServerError();
        }
        return true;
    }

    private boolean shouldFallback(int statusCode, Object body) {
        return statusCode == 429 || statusCode >= 500 || body == null;
    }
}
