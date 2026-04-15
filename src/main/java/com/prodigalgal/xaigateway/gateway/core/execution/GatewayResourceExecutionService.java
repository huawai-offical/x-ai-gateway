package com.prodigalgal.xaigateway.gateway.core.execution;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.application.CredentialCryptoService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalFileRef;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
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
import java.time.Instant;
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
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final AccountSelectionService accountSelectionService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;
    private final List<GatewayResourceExecutor> gatewayResourceExecutors;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final GatewayRequestLifecycleService gatewayRequestLifecycleService;
    private final GatewayFileService gatewayFileService;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    @Autowired
    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            List<GatewayResourceExecutor> gatewayResourceExecutors,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            GatewayFileService gatewayFileService,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.accountSelectionService = accountSelectionService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
        this.gatewayResourceExecutors = gatewayResourceExecutors;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.gatewayRequestLifecycleService = gatewayRequestLifecycleService;
        this.gatewayFileService = gatewayFileService;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
    }

    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            List<GatewayResourceExecutor> gatewayResourceExecutors,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            GatewayFileService gatewayFileService,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                new CredentialMaterialResolver(accountSelectionService, credentialCryptoService, new tools.jackson.databind.ObjectMapper()),
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                gatewayResourceExecutors,
                gatewayObservabilityService,
                gatewayRequestLifecycleService,
                gatewayFileService,
                objectMapper,
                gatewayProperties
        );
    }

    public GatewayResourceExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            List<GatewayResourceExecutor> gatewayResourceExecutors,
            GatewayFileService gatewayFileService,
            ObjectMapper objectMapper) {
        this(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
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
                new GatewayRequestLifecycleService(null, null, null, null, new tools.jackson.databind.ObjectMapper()) {
                },
                gatewayFileService,
                objectMapper,
                new GatewayProperties()
        );
    }

    public java.util.List<GatewayFileResponse> listFiles(Long distributedKeyId) {
        return gatewayFileService.listFiles(distributedKeyId);
    }

    public GatewayFileResponse getFile(String fileId, Long distributedKeyId) {
        return gatewayFileService.getFile(fileId, distributedKeyId);
    }

    public ResponseEntity<byte[]> getFileContent(String fileId, Long distributedKeyId) {
        GatewayFileContent content = gatewayFileService.getFileContent(fileId, distributedKeyId);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(content.mimeType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.metadata().filename() + "\"")
                .body(content.bytes());
    }

    public Mono<GatewayFileResponse> createFile(
            String distributedKeyPrefix,
            Long distributedKeyId,
            String purpose,
            FilePart file) {
        CanonicalResourceRequest request = buildResourceRequest(
                distributedKeyPrefix,
                "POST",
                "/v1/files",
                "resource-orchestration",
                objectMapper.createObjectNode(),
                java.util.Map.of("purpose", purpose == null ? "" : purpose),
                java.util.List.of(),
                false
        );
        return executeMultipartJson(request, distributedKeyId, "resource-orchestration", java.util.Map.of("file", file))
                .map(response -> objectMapper.convertValue(response.getBody(), GatewayFileResponse.class));
    }

    public void deleteFile(
            String distributedKeyPrefix,
            Long distributedKeyId,
            String fileId) {
        CanonicalResourceRequest request = buildResourceRequest(
                distributedKeyPrefix,
                "DELETE",
                "/v1/files/" + fileId,
                "resource-orchestration",
                null,
                java.util.Map.of(),
                java.util.List.of(),
                false
        );
        executeJson(request, distributedKeyId, "resource-orchestration");
    }

    public JsonNode executeLifecycleJson(
            Long distributedKeyId,
            String distributedKeyPrefix,
            String httpMethod,
            String requestPath,
            String requestedModel,
            JsonNode body) {
        CanonicalResourceRequest request = buildResourceRequest(
                distributedKeyPrefix,
                httpMethod,
                requestPath,
                requestedModel,
                body,
                java.util.Map.of(),
                java.util.List.of(),
                false
        );
        return executeJson(request, distributedKeyId, requestedModel).getBody();
    }

    public Mono<JsonNode> executeLifecycleMultipart(
            Long distributedKeyId,
            String distributedKeyPrefix,
            String httpMethod,
            String requestPath,
            String requestedModel,
            java.util.Map<String, String> formFields,
            java.util.List<CanonicalFileRef> fileRefs,
            java.util.Map<String, FilePart> files) {
        CanonicalResourceRequest request = buildResourceRequest(
                distributedKeyPrefix,
                httpMethod,
                requestPath,
                requestedModel,
                objectMapper.createObjectNode(),
                formFields,
                fileRefs,
                false
        );
        return executeMultipartJson(request, distributedKeyId, requestedModel, files)
                .map(ResponseEntity::getBody);
    }

    public ResponseEntity<JsonNode> executeJson(
            String distributedKeyPrefix,
            String requestPath,
            JsonNode requestBody,
            String defaultModel) {
        CanonicalResourceRequest request = buildResourceRequest(
                distributedKeyPrefix,
                "POST",
                requestPath,
                requestBody != null && requestBody.isObject() ? requestBody.path("model").asText(null) : defaultModel,
                requestBody,
                Map.of(),
                List.of(),
                false
        );
        return executeJson(request, resolveDistributedKeyId(distributedKeyPrefix), defaultModel);
    }

    public ResponseEntity<JsonNode> executeJson(
            CanonicalResourceRequest request,
            String defaultModel) {
        return executeJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), defaultModel);
    }

    public ResponseEntity<JsonNode> executeJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String defaultModel) {
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.httpMethod(), request.requestPath(), request.jsonBody());
        if (!semantics.requiresRouteSelection()) {
            GatewayResourceExecutionContext context = prepareNoRouteContext(distributedKeyId, request);
            JsonNode payload = request.jsonBody() == null ? objectMapper.createObjectNode() : request.jsonBody();
            return resolveExecutor(context).executeJson(context, payload, defaultModel);
        }
        ObjectNode payload = requireObjectPayload(request.jsonBody(), defaultModel);
        RouteSelectionResult selectionResult = select(request.distributedKeyPrefix(), request.requestPath(), payload.path("model").asText(), payload);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        var initialPlan = translationExecutionPlanCompiler.compileSelected(selectionResult, request, semantics, payload).canonicalPlan();
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, initialPlan, false, startedAt);
        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
            RuntimeException lastException = null;
            for (int index = 0; index < maxAttempts; index++) {
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, selectionResult.candidates().get(index), attempts);
                GatewayResourceExecutionContext context = prepareContext(candidateSelection, request, payload);
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
                    gatewayObservabilityService.recordRouteDecision(
                            requestId,
                            finalSelection,
                            request.requestPath(),
                            context.executionPlan().resourceType().wireName(),
                            context.executionPlan().operation().wireName(),
                            context.executionPlan().executionBackend(),
                            context.executionPlan().objectMode()
                    );
                    gatewayObservabilityService.recordCacheUsage(
                            requestId,
                            finalSelection,
                            com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage.empty(),
                            "none",
                            null,
                            request.requestPath(),
                            context.executionPlan().resourceType().wireName(),
                            context.executionPlan().operation().wireName(),
                            context.executionPlan().executionBackend(),
                            context.executionPlan().objectMode()
                    );
                    gatewayRequestLifecycleService.completeRequest(
                            requestId,
                            finalSelection,
                            request,
                            context.executionPlan(),
                            false,
                            GatewayUsageView.empty(),
                            startedAt
                    );
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
                        RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(
                                requestId,
                                failedSelection,
                                request.requestPath(),
                                context.executionPlan().resourceType().wireName(),
                                context.executionPlan().operation().wireName(),
                                context.executionPlan().executionBackend(),
                                context.executionPlan().objectMode()
                        );
                        gatewayRequestLifecycleService.failRequest(
                                requestId,
                                failedSelection,
                                request,
                                context.executionPlan(),
                                false,
                                exception,
                                GatewayUsageView.empty(),
                                startedAt
                        );
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
        CanonicalResourceRequest request = buildResourceRequest(
                distributedKeyPrefix,
                "POST",
                requestPath,
                requestBody != null && requestBody.isObject() ? requestBody.path("model").asText(null) : defaultModel,
                requestBody,
                Map.of(),
                List.of(),
                true
        );
        return executeBinaryJson(request, resolveDistributedKeyId(distributedKeyPrefix), defaultModel);
    }

    public ResponseEntity<byte[]> executeBinaryJson(
            CanonicalResourceRequest request,
            String defaultModel) {
        return executeBinaryJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), defaultModel);
    }

    public ResponseEntity<byte[]> executeBinaryJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String defaultModel) {
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.httpMethod(), request.requestPath(), request.jsonBody());
        if (!semantics.requiresRouteSelection()) {
            GatewayResourceExecutionContext context = prepareNoRouteContext(distributedKeyId, request);
            JsonNode payload = request.jsonBody() == null ? objectMapper.createObjectNode() : request.jsonBody();
            return resolveExecutor(context).executeBinary(context, payload, defaultModel);
        }
        ObjectNode payload = requireObjectPayload(request.jsonBody(), defaultModel);
        RouteSelectionResult selectionResult = select(request.distributedKeyPrefix(), request.requestPath(), payload.path("model").asText(), payload);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        var initialPlan = translationExecutionPlanCompiler.compileSelected(selectionResult, request, semantics, payload).canonicalPlan();
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, initialPlan, false, startedAt);
        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
            RuntimeException lastException = null;
            for (int index = 0; index < maxAttempts; index++) {
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, selectionResult.candidates().get(index), attempts);
                GatewayResourceExecutionContext context = prepareContext(candidateSelection, request, payload);
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
                    gatewayObservabilityService.recordRouteDecision(
                            requestId,
                            finalSelection,
                            request.requestPath(),
                            context.executionPlan().resourceType().wireName(),
                            context.executionPlan().operation().wireName(),
                            context.executionPlan().executionBackend(),
                            context.executionPlan().objectMode()
                    );
                    gatewayObservabilityService.recordCacheUsage(
                            requestId,
                            finalSelection,
                            com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage.empty(),
                            "none",
                            null,
                            request.requestPath(),
                            context.executionPlan().resourceType().wireName(),
                            context.executionPlan().operation().wireName(),
                            context.executionPlan().executionBackend(),
                            context.executionPlan().objectMode()
                    );
                    gatewayRequestLifecycleService.completeRequest(
                            requestId,
                            finalSelection,
                            request,
                            context.executionPlan(),
                            false,
                            GatewayUsageView.empty(),
                            startedAt
                    );
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
                        RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(
                                requestId,
                                failedSelection,
                                request.requestPath(),
                                context.executionPlan().resourceType().wireName(),
                                context.executionPlan().operation().wireName(),
                                context.executionPlan().executionBackend(),
                                context.executionPlan().objectMode()
                        );
                        gatewayRequestLifecycleService.failRequest(
                                requestId,
                                failedSelection,
                                request,
                                context.executionPlan(),
                                false,
                                exception,
                                GatewayUsageView.empty(),
                                startedAt
                        );
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
        CanonicalResourceRequest request = buildResourceRequest(
                distributedKeyPrefix,
                "POST",
                requestPath,
                requestedModel,
                JsonNodeFactory.instance.objectNode(),
                formFields,
                List.of(),
                false
        );
        return executeMultipartJson(request, resolveDistributedKeyId(distributedKeyPrefix), requestedModel, files);
    }

    public Mono<ResponseEntity<JsonNode>> executeMultipartJson(
            CanonicalResourceRequest request,
            String requestedModel,
            Map<String, FilePart> files) {
        return executeMultipartJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), requestedModel, files);
    }

    public Mono<ResponseEntity<JsonNode>> executeMultipartJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String requestedModel,
            Map<String, FilePart> files) {
        ObjectNode routePayload = objectPayloadForMultipart(request, requestedModel);
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.httpMethod(), request.requestPath(), routePayload);
        if (!semantics.requiresRouteSelection()) {
            GatewayResourceExecutionContext context = prepareNoRouteContext(distributedKeyId, request);
            return resolveExecutor(context).executeMultipart(context, requestedModel, request.formFields(), files);
        }
        RouteSelectionResult selectionResult = select(request.distributedKeyPrefix(), request.requestPath(), requestedModel, routePayload);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        var initialPlan = translationExecutionPlanCompiler.compileSelected(selectionResult, request, semantics, routePayload).canonicalPlan();
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, initialPlan, true, startedAt);
        List<RouteExecutionAttempt> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
        return executeMultipartAttempt(
                requestId,
                selectionResult,
                request,
                requestedModel,
                routePayload,
                request.formFields(),
                files,
                0,
                maxAttempts,
                attempts,
                startedAt
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
            CanonicalResourceRequest request,
            String requestedModel,
            JsonNode routePayload,
            Map<String, String> formFields,
            Map<String, FilePart> files,
            int candidateIndex,
            int maxAttempts,
            List<RouteExecutionAttempt> attempts,
            Instant startedAt) {
        RouteSelectionResult candidateSelection = selectionForCandidate(baseSelection, baseSelection.candidates().get(candidateIndex), attempts);
        GatewayResourceExecutionContext context = prepareContext(candidateSelection, request, routePayload);
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
                                    request,
                                    requestedModel,
                                    routePayload,
                                    formFields,
                                    files,
                                    candidateIndex + 1,
                                    maxAttempts,
                                    attempts,
                                    startedAt
                            );
                        }
                        gatewayObservabilityService.recordRouteDecision(
                                requestId,
                                candidateSelection.withAttempts(List.copyOf(attempts)),
                                request.requestPath(),
                                context.executionPlan().resourceType().wireName(),
                                context.executionPlan().operation().wireName(),
                                context.executionPlan().executionBackend(),
                                context.executionPlan().objectMode()
                        );
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
                        gatewayObservabilityService.recordRouteDecision(
                                requestId,
                                finalSelection,
                                request.requestPath(),
                                context.executionPlan().resourceType().wireName(),
                                context.executionPlan().operation().wireName(),
                                context.executionPlan().executionBackend(),
                                context.executionPlan().objectMode()
                        );
                        gatewayObservabilityService.recordCacheUsage(
                                requestId,
                                finalSelection,
                                com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage.empty(),
                                "none",
                                null,
                                request.requestPath(),
                                context.executionPlan().resourceType().wireName(),
                                context.executionPlan().operation().wireName(),
                                context.executionPlan().executionBackend(),
                                context.executionPlan().objectMode()
                        );
                        gatewayRequestLifecycleService.completeRequest(
                                requestId,
                                finalSelection,
                                request,
                                context.executionPlan(),
                                true,
                                GatewayUsageView.empty(),
                                startedAt
                        );
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
                                request,
                                requestedModel,
                                routePayload,
                                formFields,
                                files,
                                candidateIndex + 1,
                                maxAttempts,
                                attempts,
                                startedAt
                        );
                    }
                    gatewayObservabilityService.recordRouteDecision(
                            requestId,
                            candidateSelection.withAttempts(List.copyOf(attempts)),
                            request.requestPath(),
                            context.executionPlan().resourceType().wireName(),
                            context.executionPlan().operation().wireName(),
                            context.executionPlan().executionBackend(),
                            context.executionPlan().objectMode()
                    );
                    gatewayRequestLifecycleService.failRequest(
                            requestId,
                            candidateSelection.withAttempts(List.copyOf(attempts)),
                            request,
                            context.executionPlan(),
                            true,
                            error,
                            GatewayUsageView.empty(),
                            startedAt
                    );
                    return Mono.error(error);
                });
    }

    private GatewayResourceExecutionContext prepareContext(
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            JsonNode requestBody) {
        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(selectionResult, credential);
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                request.resourceType(),
                request.operation(),
                gatewayRequestFeatureService.describe(request.httpMethod(), request.requestPath(), requestBody).requiredFeatures(),
                true
        );
        var executionPlanCompilation = translationExecutionPlanCompiler.compileSelected(
                selectionResult,
                request,
                semantics,
                requestBody
        );
        return new GatewayResourceExecutionContext(
                selectionResult.distributedKeyId(),
                selectionResult,
                credential,
                credentialMaterial,
                request,
                executionPlanCompilation.canonicalPlan()
        );
    }

    private GatewayResourceExecutionContext prepareNoRouteContext(
            Long distributedKeyId,
            CanonicalResourceRequest request) {
        CanonicalResourceRequest normalizedRequest = request.normalizedPath() == null || request.normalizedPath().equals(request.requestPath())
                ? request
                : request;
        var compilation = translationExecutionPlanCompiler.compilePreview(
                request.distributedKeyPrefix(),
                request.ingressProtocol().name().toLowerCase(),
                request.httpMethod(),
                request.requestPath(),
                request.requestedModel(),
                GatewayDegradationPolicy.ALLOW_LOSSY,
                GatewayClientFamily.GENERIC_OPENAI,
                request.jsonBody()
        );
        return new GatewayResourceExecutionContext(
                distributedKeyId,
                null,
                null,
                (ResolvedCredentialMaterial) null,
                normalizedRequest,
                compilation.canonicalPlan()
        );
    }

    private GatewayResourceExecutor resolveExecutor(GatewayResourceExecutionContext context) {
        CatalogCandidateView candidate = context.selectionResult() == null ? null : context.selectionResult().selectedCandidate().candidate();
        return gatewayResourceExecutors.stream()
                .filter(executor -> context.executionPlan() == null
                        || executor.backend() == null
                        || executor.backend() == context.executionPlan().executionBackend())
                .filter(executor -> executor.supports(context.request(), candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前站点不支持该资源执行。"));
    }

    private CanonicalResourceRequest buildResourceRequest(
            String distributedKeyPrefix,
            String httpMethod,
            String requestPath,
            String requestedModel,
            JsonNode jsonBody,
            Map<String, String> formFields,
            List<CanonicalFileRef> fileRefs,
            boolean expectsBinary) {
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(httpMethod, requestPath, jsonBody);
        return new CanonicalResourceRequest(
                distributedKeyPrefix,
                CanonicalIngressProtocol.OPENAI,
                httpMethod,
                requestPath,
                gatewayRequestFeatureService.normalizePath(requestPath),
                gatewayRequestFeatureService.extractPathParams(requestPath),
                requestedModel,
                semantics.resourceType(),
                semantics.operation(),
                jsonBody,
                formFields,
                fileRefs,
                expectsBinary,
                false
        );
    }

    private ObjectNode objectPayloadForMultipart(CanonicalResourceRequest request, String requestedModel) {
        ObjectNode routePayload = request.jsonBody() != null && request.jsonBody().isObject()
                ? ((ObjectNode) request.jsonBody()).deepCopy()
                : JsonNodeFactory.instance.objectNode();
        if (requestedModel != null && !requestedModel.isBlank()) {
            routePayload.put("model", requestedModel);
        }
        request.formFields().forEach(routePayload::put);
        return routePayload;
    }

    private Long resolveDistributedKeyId(String distributedKeyPrefix) {
        return distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                .id();
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
