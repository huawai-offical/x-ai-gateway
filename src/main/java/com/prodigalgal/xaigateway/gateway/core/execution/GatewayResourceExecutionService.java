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
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalFileRef;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.DefaultCanonicalResourceMapper;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileListPage;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResponse;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailService;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceContentKind;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceDirection;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceStage;
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
import java.util.LinkedHashMap;
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
    private final CanonicalResourceMapper canonicalResourceMapper;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;
    private final GatewayRequestTraceDetailService gatewayRequestTraceDetailService;

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
            CanonicalResourceMapper canonicalResourceMapper,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties,
            GatewayRequestTraceDetailService gatewayRequestTraceDetailService) {
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
        this.canonicalResourceMapper = canonicalResourceMapper;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
        this.gatewayRequestTraceDetailService = gatewayRequestTraceDetailService;
    }

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
            CanonicalResourceMapper canonicalResourceMapper,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                credentialMaterialResolver,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                gatewayResourceExecutors,
                gatewayObservabilityService,
                gatewayRequestLifecycleService,
                gatewayFileService,
                canonicalResourceMapper,
                objectMapper,
                gatewayProperties,
                null
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
                new DefaultCanonicalResourceMapper(),
                objectMapper,
                gatewayProperties,
                null
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

    public GatewayFileListPage listFilesPage(
            Long distributedKeyId,
            String purpose,
            String after,
            Integer limit,
            String order) {
        return gatewayFileService.listFilesPage(distributedKeyId, purpose, after, limit, order);
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

    public JsonNode deleteFile(
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
        return executeJson(request, distributedKeyId, "resource-orchestration").getBody();
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
        return executeDetailedJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), defaultModel).jsonResponse();
    }

    public ResponseEntity<JsonNode> executeJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String defaultModel) {
        return executeDetailedJson(request, distributedKeyId, defaultModel).jsonResponse();
    }

    public GatewayResourceExecutionResult executeDetailedJson(
            CanonicalResourceRequest request,
            String defaultModel) {
        return executeDetailedJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), defaultModel);
    }

    public GatewayResourceExecutionResult executeDetailedJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String defaultModel) {
        GatewayRequestSemantics semantics = describeRequest(request, request.jsonBody());
        if (semantics.routeSelectionMode() != com.prodigalgal.xaigateway.gateway.core.interop.RouteSelectionMode.CATALOG_SELECTION) {
            GatewayResourceExecutionContext context = prepareNoRouteContext(distributedKeyId, request);
            JsonNode payload = request.jsonBody() == null ? objectMapper.createObjectNode() : request.jsonBody();
            String requestId = gatewayObservabilityService.nextRequestId();
            Instant startedAt = Instant.now();
            startNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, startedAt);
            recordResourceStartTrace(requestId, request, payload, context.executionPlan(), null, false);
            recordResourceAttemptTrace(requestId, request, payload, context.executionPlan(), null, false);
            try {
                GatewayResourceExecutionResult result = jsonResult(
                        requestId,
                        request,
                        context.executionPlan(),
                        resolveExecutor(context).executeJson(context, payload, defaultModel)
                );
                recordResourceResultTrace(requestId, result, false, true);
                completeNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, result.canonicalResponse(), startedAt);
                return result;
            } catch (RuntimeException exception) {
                recordTraceError(
                        requestId,
                        RequestTraceDirection.INTERNAL,
                        exception,
                        traceMetadata(RequestTraceStage.ERROR, "stream", false, "routeSelectionMode", "NO_ROUTE")
                );
                failNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, exception, startedAt);
                throw exception;
            }
        }
        ObjectNode payload = requireObjectPayload(request.jsonBody(), defaultModel);
        RouteSelectionResult selectionResult = select(request, payload.path("model").asText(), payload);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        var initialPlan = translationExecutionPlanCompiler.compileSelected(selectionResult, request, semantics, payload).canonicalPlan();
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, initialPlan, false, startedAt);
        recordResourceStartTrace(requestId, request, payload, initialPlan, selectionResult, false);
        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
            RuntimeException lastException = null;
            for (int index = 0; index < maxAttempts; index++) {
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, selectionResult.candidates().get(index), attempts);
                GatewayResourceExecutionContext context = null;
                try {
                    context = prepareContext(candidateSelection, request, payload);
                    recordResourceAttemptTrace(requestId, request, payload, context.executionPlan(), candidateSelection, false);
                    ResponseEntity<JsonNode> response = resolveExecutor(context).executeJson(context, payload, defaultModel);
                    GatewayResourceExecutionResult result = jsonResult(requestId, request, context.executionPlan(), response);
                    recordResourceResultTrace(requestId, result, false, !shouldFallback(response.getStatusCode().value(), response.getBody()));
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
                            return result;
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
                    recordStructuredRouteDecision(requestId, finalSelection, request, context.executionPlan());
                    recordStructuredCacheUsage(requestId, finalSelection, request, context.executionPlan());
                    gatewayRequestLifecycleService.completeRequest(
                            requestId,
                            finalSelection,
                            request,
                            context.executionPlan(),
                            false,
                            GatewayUsageView.empty(),
                            result.canonicalResponse(),
                            startedAt,
                            context.credentialMaterial() == null ? null : context.credentialMaterial().accountId(),
                            null
                    );
                    return result;
                } catch (RuntimeException exception) {
                    CanonicalExecutionPlan failedPlan = failedPlan(context, exception);
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            exception.getMessage()
                    ));
                    recordCandidateFailure(candidateSelection, exception);
                    recordResourceAttemptErrorTrace(requestId, candidateSelection, failedPlan, exception, false, false, index + 1);
                    lastException = exception;
                    if (!shouldFallback(exception) || index == maxAttempts - 1) {
                        RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        recordStructuredRouteDecision(requestId, failedSelection, request, failedPlan);
                        gatewayRequestLifecycleService.failRequest(
                                requestId,
                                failedSelection,
                                request,
                                failedPlan,
                                false,
                                exception,
                                GatewayUsageView.empty(),
                                startedAt,
                                context == null || context.credentialMaterial() == null ? null : context.credentialMaterial().accountId(),
                                null
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
        return executeDetailedBinaryJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), defaultModel).binaryResponse();
    }

    public ResponseEntity<byte[]> executeBinaryJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String defaultModel) {
        return executeDetailedBinaryJson(request, distributedKeyId, defaultModel).binaryResponse();
    }

    public GatewayResourceExecutionResult executeDetailedBinaryJson(
            CanonicalResourceRequest request,
            String defaultModel) {
        return executeDetailedBinaryJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), defaultModel);
    }

    public GatewayResourceExecutionResult executeDetailedBinaryJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String defaultModel) {
        GatewayRequestSemantics semantics = describeRequest(request, request.jsonBody());
        if (semantics.routeSelectionMode() != com.prodigalgal.xaigateway.gateway.core.interop.RouteSelectionMode.CATALOG_SELECTION) {
            GatewayResourceExecutionContext context = prepareNoRouteContext(distributedKeyId, request);
            JsonNode payload = request.jsonBody() == null ? objectMapper.createObjectNode() : request.jsonBody();
            String requestId = gatewayObservabilityService.nextRequestId();
            Instant startedAt = Instant.now();
            startNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, startedAt);
            recordResourceStartTrace(requestId, request, payload, context.executionPlan(), null, false);
            recordResourceAttemptTrace(requestId, request, payload, context.executionPlan(), null, false);
            try {
                GatewayResourceExecutionResult result = binaryResult(
                        requestId,
                        request,
                        context.executionPlan(),
                        resolveExecutor(context).executeBinary(context, payload, defaultModel)
                );
                recordResourceResultTrace(requestId, result, false, true);
                completeNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, result.canonicalResponse(), startedAt);
                return result;
            } catch (RuntimeException exception) {
                recordTraceError(
                        requestId,
                        RequestTraceDirection.INTERNAL,
                        exception,
                        traceMetadata(RequestTraceStage.ERROR, "stream", false, "routeSelectionMode", "NO_ROUTE", "expectsBinary", true)
                );
                failNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, exception, startedAt);
                throw exception;
            }
        }
        ObjectNode payload = requireObjectPayload(request.jsonBody(), defaultModel);
        RouteSelectionResult selectionResult = select(request, payload.path("model").asText(), payload);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        var initialPlan = translationExecutionPlanCompiler.compileSelected(selectionResult, request, semantics, payload).canonicalPlan();
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, initialPlan, false, startedAt);
        recordResourceStartTrace(requestId, request, payload, initialPlan, selectionResult, false);
        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
            RuntimeException lastException = null;
            for (int index = 0; index < maxAttempts; index++) {
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, selectionResult.candidates().get(index), attempts);
                GatewayResourceExecutionContext context = null;
                try {
                    context = prepareContext(candidateSelection, request, payload);
                    recordResourceAttemptTrace(requestId, request, payload, context.executionPlan(), candidateSelection, false);
                    ResponseEntity<byte[]> response = resolveExecutor(context).executeBinary(context, payload, defaultModel);
                    GatewayResourceExecutionResult result = binaryResult(requestId, request, context.executionPlan(), response);
                    recordResourceResultTrace(requestId, result, false, !shouldFallback(response.getStatusCode().value(), response.getBody()));
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
                            return result;
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
                    recordStructuredRouteDecision(requestId, finalSelection, request, context.executionPlan());
                    recordStructuredCacheUsage(requestId, finalSelection, request, context.executionPlan());
                    gatewayRequestLifecycleService.completeRequest(
                            requestId,
                            finalSelection,
                            request,
                            context.executionPlan(),
                            false,
                            GatewayUsageView.empty(),
                            result.canonicalResponse(),
                            startedAt,
                            context.credentialMaterial() == null ? null : context.credentialMaterial().accountId(),
                            null
                    );
                    return result;
                } catch (RuntimeException exception) {
                    CanonicalExecutionPlan failedPlan = failedPlan(context, exception);
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            exception.getMessage()
                    ));
                    recordCandidateFailure(candidateSelection, exception);
                    recordResourceAttemptErrorTrace(requestId, candidateSelection, failedPlan, exception, false, true, index + 1);
                    lastException = exception;
                    if (!shouldFallback(exception) || index == maxAttempts - 1) {
                        RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        recordStructuredRouteDecision(requestId, failedSelection, request, failedPlan);
                        gatewayRequestLifecycleService.failRequest(
                                requestId,
                                failedSelection,
                                request,
                                failedPlan,
                                false,
                                exception,
                                GatewayUsageView.empty(),
                                startedAt,
                                context == null || context.credentialMaterial() == null ? null : context.credentialMaterial().accountId(),
                                null
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
        return executeDetailedMultipartJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), requestedModel, files)
                .map(GatewayResourceExecutionResult::jsonResponse);
    }

    public Mono<ResponseEntity<JsonNode>> executeMultipartJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String requestedModel,
            Map<String, FilePart> files) {
        return executeDetailedMultipartJson(request, distributedKeyId, requestedModel, files)
                .map(GatewayResourceExecutionResult::jsonResponse);
    }

    public Mono<GatewayResourceExecutionResult> executeDetailedMultipartJson(
            CanonicalResourceRequest request,
            String requestedModel,
            Map<String, FilePart> files) {
        return executeDetailedMultipartJson(request, resolveDistributedKeyId(request.distributedKeyPrefix()), requestedModel, files);
    }

    public Mono<GatewayResourceExecutionResult> executeDetailedMultipartJson(
            CanonicalResourceRequest request,
            Long distributedKeyId,
            String requestedModel,
            Map<String, FilePart> files) {
        ObjectNode routePayload = objectPayloadForMultipart(request, requestedModel);
        GatewayRequestSemantics semantics = describeRequest(request, routePayload);
        if (semantics.routeSelectionMode() != com.prodigalgal.xaigateway.gateway.core.interop.RouteSelectionMode.CATALOG_SELECTION) {
            GatewayResourceExecutionContext context = prepareNoRouteContext(distributedKeyId, request);
            String requestId = gatewayObservabilityService.nextRequestId();
            Instant startedAt = Instant.now();
            startNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, startedAt);
            recordResourceStartTrace(requestId, request, routePayload, context.executionPlan(), null, true);
            recordResourceAttemptTrace(requestId, request, routePayload, context.executionPlan(), null, true);
            return resolveExecutor(context).executeMultipart(context, requestedModel, request.formFields(), files)
                    .map(response -> {
                        GatewayResourceExecutionResult result = jsonResult(requestId, request, context.executionPlan(), response);
                        recordResourceResultTrace(requestId, result, true, true);
                        completeNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, result.canonicalResponse(), startedAt);
                        return result;
                    })
                    .onErrorResume(error -> {
                        recordTraceError(
                                requestId,
                                RequestTraceDirection.INTERNAL,
                                error,
                                traceMetadata(RequestTraceStage.ERROR, "stream", true, "routeSelectionMode", "NO_ROUTE", "multipart", true)
                        );
                        failNoRouteRequest(requestId, distributedKeyId, request, context.executionPlan(), false, error, startedAt);
                        return Mono.error(error);
                    });
        }
        RouteSelectionResult selectionResult = select(request, requestedModel, routePayload);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        var initialPlan = translationExecutionPlanCompiler.compileSelected(selectionResult, request, semantics, routePayload).canonicalPlan();
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, initialPlan, true, startedAt);
        recordResourceStartTrace(requestId, request, routePayload, initialPlan, selectionResult, true);
        List<RouteExecutionAttempt> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
        return Mono.defer(() -> executeMultipartAttempt(
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
                ))
                .doFinally(signalType -> distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey()));
    }

    private RouteSelectionResult select(
            CanonicalResourceRequest request,
            String requestedModel,
            Object requestBody) {
        return gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.ingressProtocol().name().toLowerCase(),
                selectionPath(request),
                requestedModel,
                requestBody,
                GatewayClientFamily.GENERIC_OPENAI,
                true,
                null,
                request.httpMethod()
        ));
    }

    private Mono<GatewayResourceExecutionResult> executeMultipartAttempt(
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
        GatewayResourceExecutionContext context;
        try {
            context = prepareContext(candidateSelection, request, routePayload);
            recordResourceAttemptTrace(requestId, request, routePayload, context.executionPlan(), candidateSelection, true);
        } catch (RuntimeException exception) {
            CanonicalExecutionPlan failedPlan = failedPlan(null, exception);
            attempts.add(new RouteExecutionAttempt(
                    candidateIndex + 1,
                    candidateSelection.selectedCandidate().candidate().credentialId(),
                    candidateSelection.selectedCandidate().candidate().providerType().name(),
                    "FAILED_BEFORE_FIRST_BYTE",
                    exception.getMessage()
            ));
            RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
            recordStructuredRouteDecision(requestId, failedSelection, request, failedPlan);
            recordResourceAttemptErrorTrace(requestId, candidateSelection, failedPlan, exception, true, false, candidateIndex + 1);
            gatewayRequestLifecycleService.failRequest(
                    requestId,
                    failedSelection,
                    request,
                    failedPlan,
                    true,
                    exception,
                    GatewayUsageView.empty(),
                    startedAt,
                    null,
                    null
            );
            return Mono.error(exception);
        }
        return resolveExecutor(context).executeMultipart(context, requestedModel, formFields, files)
                .flatMap(response -> {
                    GatewayResourceExecutionResult result = jsonResult(requestId, request, context.executionPlan(), response);
                    recordResourceResultTrace(requestId, result, true, !shouldFallback(response.getStatusCode().value(), response.getBody()));
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
                        recordStructuredRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)), request, context.executionPlan());
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
                        recordStructuredRouteDecision(requestId, finalSelection, request, context.executionPlan());
                        recordStructuredCacheUsage(requestId, finalSelection, request, context.executionPlan());
                        gatewayRequestLifecycleService.completeRequest(
                                requestId,
                                finalSelection,
                                request,
                                context.executionPlan(),
                                true,
                                GatewayUsageView.empty(),
                                result.canonicalResponse(),
                                startedAt,
                                context.credentialMaterial() == null ? null : context.credentialMaterial().accountId(),
                                null
                        );
                    }
                    return Mono.just(result);
                })
                .onErrorResume(error -> {
                    attempts.add(new RouteExecutionAttempt(
                            candidateIndex + 1,
                            candidateSelection.selectedCandidate().candidate().credentialId(),
                            candidateSelection.selectedCandidate().candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            error.getMessage()
                    ));
                    recordCandidateFailure(candidateSelection, error);
                    recordResourceAttemptErrorTrace(requestId, candidateSelection, context.executionPlan(), error, true, false, candidateIndex + 1);
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
                    recordStructuredRouteDecision(requestId, candidateSelection.withAttempts(List.copyOf(attempts)), request, context.executionPlan());
                    gatewayRequestLifecycleService.failRequest(
                            requestId,
                            candidateSelection.withAttempts(List.copyOf(attempts)),
                            request,
                            context.executionPlan(),
                            true,
                            error,
                            GatewayUsageView.empty(),
                            startedAt,
                            context.credentialMaterial() == null ? null : context.credentialMaterial().accountId(),
                            null
                    );
                    return Mono.error(error);
                });
    }

    private GatewayResourceExecutionContext prepareContext(
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            JsonNode requestBody) {
        GatewayRequestSemantics semantics = new GatewayRequestSemantics(
                request.resourceType(),
                request.operation(),
                describeRequest(request, requestBody).requiredFeatures(),
                describeRequest(request, requestBody).routeSelectionMode()
        );
        var executionPlanCompilation = translationExecutionPlanCompiler.compileSelected(
                selectionResult,
                request,
                semantics,
                requestBody
        );
        CanonicalExecutionPlan executionPlan = ensureExecutable(executionPlanCompilation.canonicalPlan());
        UpstreamCredentialEntity credential = getRequiredCredential(selectionResult.selectedCandidate().candidate().credentialId());
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(selectionResult, credential);
        return new GatewayResourceExecutionContext(
                selectionResult.distributedKeyId(),
                selectionResult,
                credential,
                credentialMaterial,
                request,
                executionPlan
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
                GatewayDegradationPolicy.STRICT,
                GatewayClientFamily.GENERIC_OPENAI,
                request.jsonBody()
        );
        return new GatewayResourceExecutionContext(
                distributedKeyId,
                null,
                null,
                (ResolvedCredentialMaterial) null,
                normalizedRequest,
                ensureExecutable(compilation.canonicalPlan())
        );
    }

    private void startNoRouteRequest(
            String requestId,
            Long distributedKeyId,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            Instant startedAt) {
        gatewayRequestLifecycleService.startRequest(
                requestId,
                distributedKeyId,
                request.distributedKeyPrefix(),
                request.ingressProtocol().name().toLowerCase(java.util.Locale.ROOT),
                request,
                plan,
                stream,
                startedAt
        );
    }

    private void completeNoRouteRequest(
            String requestId,
            Long distributedKeyId,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            CanonicalResourceResponse canonicalResponse,
            Instant startedAt) {
        gatewayRequestLifecycleService.completeRequest(
                requestId,
                distributedKeyId,
                request.distributedKeyPrefix(),
                request.ingressProtocol().name().toLowerCase(java.util.Locale.ROOT),
                request,
                plan,
                stream,
                GatewayUsageView.empty(),
                canonicalResponse,
                startedAt
        );
    }

    private void failNoRouteRequest(
            String requestId,
            Long distributedKeyId,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            boolean stream,
            Throwable error,
            Instant startedAt) {
        gatewayRequestLifecycleService.failRequest(
                requestId,
                distributedKeyId,
                request.distributedKeyPrefix(),
                request.ingressProtocol().name().toLowerCase(java.util.Locale.ROOT),
                request,
                plan,
                stream,
                error,
                startedAt
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

    private CanonicalExecutionPlan ensureExecutable(CanonicalExecutionPlan executionPlan) {
        if (executionPlan == null) {
            return null;
        }
        if (executionPlan.executable()) {
            return executionPlan;
        }
        if (!executionPlan.blockerReasons().isEmpty()) {
            throw new BlockedExecutionPlanException(executionPlan, String.join("；", executionPlan.blockerReasons()));
        }
        throw new BlockedExecutionPlanException(executionPlan, "当前请求在 planner 阶段被阻止执行。");
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

    private GatewayRequestSemantics describeRequest(CanonicalResourceRequest request, JsonNode requestBody) {
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.httpMethod(), request.requestPath(), requestBody);
        if (semantics != null) {
            return semantics;
        }
        return gatewayRequestFeatureService.describe(request.httpMethod(), selectionPath(request), requestBody);
    }

    private String selectionPath(CanonicalResourceRequest request) {
        return request.normalizedPath() == null || request.normalizedPath().isBlank()
                ? request.requestPath()
                : request.normalizedPath();
    }

    private CanonicalExecutionPlan failedPlan(
            GatewayResourceExecutionContext context,
            RuntimeException exception) {
        if (context != null) {
            return context.executionPlan();
        }
        if (exception instanceof BlockedExecutionPlanException blocked) {
            return blocked.executionPlan();
        }
        return null;
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
        if (throwable instanceof BlockedExecutionPlanException) {
            return false;
        }
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

    private void recordCandidateFailure(RouteSelectionResult candidateSelection, Throwable throwable) {
        if (throwable instanceof BlockedExecutionPlanException) {
            return;
        }
        String reason = throwable == null ? null : throwable.getMessage();
        gatewayRouteSelectionService.invalidateSelection(candidateSelection);
        gatewayRouteSelectionService.markCredentialCooldown(candidateSelection.selectedCandidate().candidate().credentialId(), reason);
    }

    private boolean shouldFallback(int statusCode, Object body) {
        return statusCode == 429 || statusCode >= 500 || body == null;
    }

    private GatewayResourceExecutionResult jsonResult(
            String requestId,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            ResponseEntity<JsonNode> response) {
        CanonicalResourceResponse canonicalResponse = canonicalResourceMapper.mapJson(request, plan, response.getBody());
        return GatewayResourceExecutionResult.json(
                requestId,
                gatewayResourceKey(request, plan, canonicalResponse),
                response,
                canonicalResponse
        );
    }

    private GatewayResourceExecutionResult binaryResult(
            String requestId,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            ResponseEntity<byte[]> response) {
        String contentType = response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString();
        CanonicalResourceResponse canonicalResponse = canonicalResourceMapper.mapBinary(request, plan, response.getBody(), contentType);
        return GatewayResourceExecutionResult.binary(
                requestId,
                gatewayResourceKey(request, plan, canonicalResponse),
                response,
                canonicalResponse
        );
    }

    private String gatewayResourceKey(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            CanonicalResourceResponse canonicalResponse) {
        if (plan == null || !supportsGatewayResourceKey(plan.resourceType())) {
            return null;
        }
        if (canonicalResponse != null && canonicalResponse.objectId() != null && !canonicalResponse.objectId().isBlank()) {
            return canonicalResponse.objectId();
        }
        if (request == null || request.pathParams().isEmpty()) {
            return null;
        }
        return request.pathParams().values().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private boolean supportsGatewayResourceKey(TranslationResourceType resourceType) {
        if (resourceType == null) {
            return false;
        }
        return resourceType == TranslationResourceType.RESPONSE
                || resourceType == TranslationResourceType.UPLOAD;
    }

    private void recordResourceStartTrace(
            String requestId,
            CanonicalResourceRequest request,
            JsonNode payload,
            CanonicalExecutionPlan plan,
            RouteSelectionResult selectionResult,
            boolean stream) {
        recordTrace(
                requestId,
                RequestTraceStage.DOWNSTREAM_REQUEST,
                RequestTraceDirection.DOWNSTREAM,
                RequestTraceContentKind.JSON,
                payload == null ? resourceRequestSnapshot(request, stream) : payload,
                resourceTraceMetadata(RequestTraceStage.DOWNSTREAM_REQUEST, request, plan, selectionResult, stream)
        );
        recordTrace(
                requestId,
                RequestTraceStage.CANONICAL_REQUEST,
                RequestTraceDirection.INTERNAL,
                RequestTraceContentKind.JSON,
                resourceRequestSnapshot(request, stream),
                resourceTraceMetadata(RequestTraceStage.CANONICAL_REQUEST, request, plan, selectionResult, stream)
        );
        recordTrace(
                requestId,
                RequestTraceStage.TRANSLATION_PLAN,
                RequestTraceDirection.INTERNAL,
                RequestTraceContentKind.JSON,
                translationPlanSnapshot(plan),
                resourceTraceMetadata(RequestTraceStage.TRANSLATION_PLAN, request, plan, selectionResult, stream)
        );
    }

    private void recordResourceAttemptTrace(
            String requestId,
            CanonicalResourceRequest request,
            JsonNode payload,
            CanonicalExecutionPlan plan,
            RouteSelectionResult selectionResult,
            boolean stream) {
        recordTrace(
                requestId,
                RequestTraceStage.UPSTREAM_REQUEST,
                RequestTraceDirection.UPSTREAM,
                RequestTraceContentKind.JSON,
                upstreamResourceRequestSnapshot(selectionResult, request, plan, payload, stream),
                resourceTraceMetadata(RequestTraceStage.UPSTREAM_REQUEST, request, plan, selectionResult, stream)
        );
    }

    private void recordResourceResultTrace(
            String requestId,
            GatewayResourceExecutionResult result,
            boolean stream,
            boolean downstreamVisible) {
        RequestTraceContentKind kind = result.binary() ? RequestTraceContentKind.BINARY_REFERENCE : RequestTraceContentKind.JSON;
        Map<String, Object> payload = downstreamResourceResponseSnapshot(result, stream);
        recordTrace(
                requestId,
                RequestTraceStage.UPSTREAM_RESPONSE,
                RequestTraceDirection.UPSTREAM,
                kind,
                payload,
                traceMetadata(
                        RequestTraceStage.UPSTREAM_RESPONSE,
                        "stream", stream,
                        "statusCode", result.statusCode(),
                        "contentType", result.contentType(),
                        "binary", result.binary()
                )
        );
        if (!downstreamVisible) {
            return;
        }
        recordTrace(
                requestId,
                RequestTraceStage.DOWNSTREAM_RESPONSE,
                RequestTraceDirection.DOWNSTREAM,
                kind,
                payload,
                traceMetadata(
                        RequestTraceStage.DOWNSTREAM_RESPONSE,
                        "stream", stream,
                        "statusCode", result.statusCode(),
                        "contentType", result.contentType(),
                        "binary", result.binary()
                )
        );
    }

    private void recordResourceAttemptErrorTrace(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalExecutionPlan plan,
            Throwable error,
            boolean stream,
            boolean expectsBinary,
            int attempt) {
        recordTraceError(
                requestId,
                RequestTraceDirection.UPSTREAM,
                error,
                traceMetadata(
                        RequestTraceStage.ERROR,
                        "stream", stream,
                        "attempt", attempt,
                        "providerType", providerTypeName(selectionResult),
                        "credentialId", credentialId(selectionResult),
                        "backend", backendName(plan),
                        "expectsBinary", expectsBinary
                )
        );
    }

    private Map<String, Object> resourceRequestSnapshot(CanonicalResourceRequest request, boolean stream) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (request == null) {
            return snapshot;
        }
        putIfPresent(snapshot, "ingressProtocol", request.ingressProtocol() == null ? null : request.ingressProtocol().name());
        putIfPresent(snapshot, "httpMethod", request.httpMethod());
        putIfPresent(snapshot, "requestPath", request.requestPath());
        putIfPresent(snapshot, "normalizedPath", request.normalizedPath());
        putIfPresent(snapshot, "requestedModel", request.requestedModel());
        putIfPresent(snapshot, "resourceType", request.resourceType() == null ? null : request.resourceType().wireName());
        putIfPresent(snapshot, "operation", request.operation() == null ? null : request.operation().wireName());
        snapshot.put("expectsBinary", request.expectsBinary());
        snapshot.put("stream", stream || request.stream());
        if (!request.pathParams().isEmpty()) {
            snapshot.put("pathParams", request.pathParams());
        }
        if (!request.formFields().isEmpty()) {
            snapshot.put("formFieldNames", request.formFields().keySet());
        }
        if (!request.fileRefs().isEmpty()) {
            snapshot.put("fileRefs", fileRefSnapshots(request.fileRefs()));
        }
        return snapshot;
    }

    private Map<String, Object> translationPlanSnapshot(CanonicalExecutionPlan plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (plan == null) {
            return snapshot;
        }
        snapshot.put("executable", plan.executable());
        putIfPresent(snapshot, "requestPath", plan.requestPath());
        putIfPresent(snapshot, "normalizedPath", plan.normalizedPath());
        putIfPresent(snapshot, "surface", plan.surface());
        putIfPresent(snapshot, "requestedModel", plan.requestedModel());
        putIfPresent(snapshot, "publicModel", plan.publicModel());
        putIfPresent(snapshot, "resolvedModel", plan.resolvedModel());
        putIfPresent(snapshot, "resourceType", plan.resourceType() == null ? null : plan.resourceType().wireName());
        putIfPresent(snapshot, "operation", plan.operation() == null ? null : plan.operation().wireName());
        putIfPresent(snapshot, "executionKind", plan.executionKind() == null ? null : plan.executionKind().name());
        putIfPresent(snapshot, "executionBackend", backendName(plan));
        putIfPresent(snapshot, "supportStatus", plan.supportStatus() == null ? null : plan.supportStatus().name());
        putIfPresent(snapshot, "objectMode", plan.objectMode());
        putIfPresent(snapshot, "backendReason", plan.backendReason());
        putIfPresent(snapshot, "degradationLevel", plan.degradationLevel() == null ? null : plan.degradationLevel().name());
        putIfPresent(snapshot, "executionCapabilityLevel", plan.executionCapabilityLevel() == null ? null : plan.executionCapabilityLevel().name());
        putIfPresent(snapshot, "renderCapabilityLevel", plan.renderCapabilityLevel() == null ? null : plan.renderCapabilityLevel().name());
        putIfPresent(snapshot, "overallCapabilityLevel", plan.overallCapabilityLevel() == null ? null : plan.overallCapabilityLevel().name());
        if (!plan.requiredFeatures().isEmpty()) {
            snapshot.put("requiredFeatures", plan.requiredFeatures());
        }
        if (!plan.featureLevels().isEmpty()) {
            snapshot.put("featureLevels", plan.featureLevels());
        }
        if (!plan.degradations().isEmpty()) {
            snapshot.put("degradations", plan.degradations());
        }
        if (!plan.blockerReasons().isEmpty()) {
            snapshot.put("blockerReasons", plan.blockerReasons());
        }
        putIfPresent(snapshot, "routeSelectionMode", plan.routeSelectionMode() == null ? null : plan.routeSelectionMode().name());
        putIfPresent(snapshot, "routePolicyReason", plan.routePolicyReason());
        putIfPresent(snapshot, "renderPolicyReason", plan.renderPolicyReason());
        putIfPresent(snapshot, "fallbackPolicyReason", plan.fallbackPolicyReason());
        return snapshot;
    }

    private Map<String, Object> upstreamResourceRequestSnapshot(
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            JsonNode payload,
            boolean stream) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "providerType", providerTypeName(selectionResult));
        putIfPresent(snapshot, "credentialId", credentialId(selectionResult));
        putIfPresent(snapshot, "credentialName", credentialName(selectionResult));
        putIfPresent(snapshot, "baseUrl", baseUrl(selectionResult));
        putIfPresent(snapshot, "model", modelName(selectionResult, request, plan));
        putIfPresent(snapshot, "httpMethod", request == null ? null : request.httpMethod());
        putIfPresent(snapshot, "requestPath", request == null ? null : request.requestPath());
        putIfPresent(snapshot, "normalizedPath", request == null ? null : request.normalizedPath());
        putIfPresent(snapshot, "resourceType", plan == null || plan.resourceType() == null ? null : plan.resourceType().wireName());
        putIfPresent(snapshot, "operation", plan == null || plan.operation() == null ? null : plan.operation().wireName());
        putIfPresent(snapshot, "executionBackend", backendName(plan));
        putIfPresent(snapshot, "supportStatus", plan == null || plan.supportStatus() == null ? null : plan.supportStatus().name());
        putIfPresent(snapshot, "degradationLevel", plan == null || plan.degradationLevel() == null ? null : plan.degradationLevel().name());
        snapshot.put("expectsBinary", request != null && request.expectsBinary());
        snapshot.put("stream", stream || (request != null && request.stream()));
        if (request != null && !request.formFields().isEmpty()) {
            snapshot.put("formFieldNames", request.formFields().keySet());
        }
        if (request != null && !request.fileRefs().isEmpty()) {
            snapshot.put("fileRefs", fileRefSnapshots(request.fileRefs()));
        }
        putIfPresent(snapshot, "payload", payload);
        return snapshot;
    }

    private Map<String, Object> downstreamResourceResponseSnapshot(GatewayResourceExecutionResult result, boolean stream) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("statusCode", result.statusCode());
        putIfPresent(snapshot, "contentType", result.contentType());
        snapshot.put("binary", result.binary());
        snapshot.put("stream", stream);
        putIfPresent(snapshot, "binaryLength", result.binaryLength());
        CanonicalResourceResponse canonicalResponse = result.canonicalResponse();
        if (canonicalResponse != null) {
            putIfPresent(snapshot, "resourceType", canonicalResponse.resourceType() == null ? null : canonicalResponse.resourceType().wireName());
            putIfPresent(snapshot, "operation", canonicalResponse.operation() == null ? null : canonicalResponse.operation().wireName());
            putIfPresent(snapshot, "responseKind", canonicalResponse.responseKind());
            putIfPresent(snapshot, "objectType", canonicalResponse.objectType());
            putIfPresent(snapshot, "objectId", canonicalResponse.objectId());
            putIfPresent(snapshot, "status", canonicalResponse.status());
            if (!canonicalResponse.events().isEmpty()) {
                snapshot.put("events", canonicalResponse.events());
            }
            if (!canonicalResponse.degradations().isEmpty()) {
                snapshot.put("degradations", canonicalResponse.degradations());
            }
            if (!canonicalResponse.metadata().isEmpty()) {
                snapshot.put("metadata", canonicalResponse.metadata());
            }
            if (canonicalResponse.body() != null && !result.binary()) {
                snapshot.put("body", canonicalResponse.body());
            }
        } else if (!result.binary()) {
            putIfPresent(snapshot, "body", result.responseJson());
        }
        return snapshot;
    }

    private List<Map<String, Object>> fileRefSnapshots(List<CanonicalFileRef> fileRefs) {
        return fileRefs.stream()
                .map(fileRef -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    putIfPresent(snapshot, "fieldName", fileRef.fieldName());
                    putIfPresent(snapshot, "fileKey", fileRef.fileKey());
                    putIfPresent(snapshot, "filename", fileRef.filename());
                    putIfPresent(snapshot, "mimeType", fileRef.mimeType());
                    return snapshot;
                })
                .toList();
    }

    private Map<String, Object> resourceTraceMetadata(
            RequestTraceStage stage,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            RouteSelectionResult selectionResult,
            boolean stream) {
        return traceMetadata(
                stage,
                "httpMethod", request == null ? null : request.httpMethod(),
                "requestPath", request == null ? null : request.requestPath(),
                "normalizedPath", request == null ? null : request.normalizedPath(),
                "resourceType", resourceTypeName(request, plan),
                "operation", operationName(request, plan),
                "providerType", providerTypeName(selectionResult),
                "credentialId", credentialId(selectionResult),
                "backend", backendName(plan),
                "stream", stream || (request != null && request.stream()),
                "expectsBinary", request == null ? null : request.expectsBinary()
        );
    }

    private void recordTrace(
            String requestId,
            RequestTraceStage stage,
            RequestTraceDirection direction,
            RequestTraceContentKind contentKind,
            Object payload,
            Map<String, ?> metadata) {
        if (gatewayRequestTraceDetailService == null) {
            return;
        }
        gatewayRequestTraceDetailService.record(requestId, stage, direction, contentKind, payload, metadata);
    }

    private void recordTraceError(
            String requestId,
            RequestTraceDirection direction,
            Throwable error,
            Map<String, ?> metadata) {
        if (gatewayRequestTraceDetailService == null) {
            return;
        }
        gatewayRequestTraceDetailService.recordError(requestId, RequestTraceStage.ERROR, direction, error, metadata);
    }

    private Map<String, Object> traceMetadata(RequestTraceStage stage, Object... entries) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("payloadSource", payloadSource(stage));
        metadata.put("wireBody", stage == RequestTraceStage.DOWNSTREAM_REQUEST);
        metadata.put("wireBodyLimitation", wireBodyLimitation(stage));
        if (entries == null) {
            return metadata;
        }
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object key = entries[index];
            Object value = entries[index + 1];
            if (key != null && value != null) {
                metadata.put(String.valueOf(key), value);
            }
        }
        return metadata;
    }

    private String payloadSource(RequestTraceStage stage) {
        if (stage == null) {
            return "gateway_resource_trace_snapshot";
        }
        return switch (stage) {
            case DOWNSTREAM_REQUEST -> "downstream_parsed_resource_request_body";
            case CANONICAL_REQUEST -> "gateway_canonical_resource_request_model";
            case TRANSLATION_PLAN -> "gateway_resource_translation_execution_plan";
            case UPSTREAM_REQUEST -> "gateway_constructed_upstream_resource_request_summary";
            case UPSTREAM_RESPONSE -> "gateway_resource_executor_response_summary";
            case DOWNSTREAM_RESPONSE -> "gateway_downstream_resource_response_summary";
            case ERROR -> "gateway_resource_error_summary";
            case CUSTOM -> "gateway_resource_custom_trace_snapshot";
        };
    }

    private String wireBodyLimitation(RequestTraceStage stage) {
        if (stage == RequestTraceStage.DOWNSTREAM_REQUEST) {
            return "parsed downstream resource body after gateway ingress handling; binary content is stored as references only";
        }
        if (stage == RequestTraceStage.UPSTREAM_REQUEST || stage == RequestTraceStage.UPSTREAM_RESPONSE) {
            return "structured gateway/executor summary, not raw upstream HTTP wire body";
        }
        return "structured gateway resource snapshot, not raw HTTP wire body";
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target != null && key != null && value != null) {
            target.put(key, value);
        }
    }

    private String resourceTypeName(CanonicalResourceRequest request, CanonicalExecutionPlan plan) {
        if (plan != null && plan.resourceType() != null) {
            return plan.resourceType().wireName();
        }
        return request == null || request.resourceType() == null ? null : request.resourceType().wireName();
    }

    private String operationName(CanonicalResourceRequest request, CanonicalExecutionPlan plan) {
        if (plan != null && plan.operation() != null) {
            return plan.operation().wireName();
        }
        return request == null || request.operation() == null ? null : request.operation().wireName();
    }

    private String backendName(CanonicalExecutionPlan plan) {
        return plan == null || plan.executionBackend() == null ? null : plan.executionBackend().name();
    }

    private CatalogCandidateView selectedCandidate(RouteSelectionResult selectionResult) {
        if (selectionResult == null || selectionResult.selectedCandidate() == null) {
            return null;
        }
        return selectionResult.selectedCandidate().candidate();
    }

    private Long credentialId(RouteSelectionResult selectionResult) {
        CatalogCandidateView candidate = selectedCandidate(selectionResult);
        return candidate == null ? null : candidate.credentialId();
    }

    private String credentialName(RouteSelectionResult selectionResult) {
        CatalogCandidateView candidate = selectedCandidate(selectionResult);
        return candidate == null ? null : candidate.credentialName();
    }

    private String providerTypeName(RouteSelectionResult selectionResult) {
        CatalogCandidateView candidate = selectedCandidate(selectionResult);
        return candidate == null || candidate.providerType() == null ? null : candidate.providerType().name();
    }

    private String baseUrl(RouteSelectionResult selectionResult) {
        CatalogCandidateView candidate = selectedCandidate(selectionResult);
        return candidate == null ? null : candidate.baseUrl();
    }

    private String modelName(
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan) {
        CatalogCandidateView candidate = selectedCandidate(selectionResult);
        if (candidate != null && candidate.modelName() != null) {
            return candidate.modelName();
        }
        if (plan != null && plan.resolvedModel() != null) {
            return plan.resolvedModel();
        }
        return request == null ? null : request.requestedModel();
    }

    private void recordStructuredRouteDecision(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan) {
        gatewayObservabilityService.recordRouteDecision(
                requestId,
                selectionResult,
                request.requestPath(),
                plan.resourceType().wireName(),
                plan.operation().wireName(),
                plan.executionBackend(),
                plan.supportStatus(),
                plan.objectMode(),
                plan.degradationLevel()
        );
    }

    private void recordStructuredCacheUsage(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan) {
        gatewayObservabilityService.recordCacheUsage(
                requestId,
                selectionResult,
                com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage.empty(),
                "none",
                null,
                request.requestPath(),
                plan.resourceType().wireName(),
                plan.operation().wireName(),
                plan.executionBackend(),
                plan.supportStatus(),
                plan.objectMode(),
                plan.degradationLevel()
        );
    }

    private static class BlockedExecutionPlanException extends IllegalArgumentException {
        private final CanonicalExecutionPlan executionPlan;

        private BlockedExecutionPlanException(CanonicalExecutionPlan executionPlan, String message) {
            super(message);
            this.executionPlan = executionPlan;
        }

        private CanonicalExecutionPlan executionPlan() {
            return executionPlan;
        }
    }
}
