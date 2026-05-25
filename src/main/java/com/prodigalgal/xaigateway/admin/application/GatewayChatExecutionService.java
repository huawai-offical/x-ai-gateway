package com.prodigalgal.xaigateway.admin.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.admin.api.AdminChatExecuteRequest;
import com.prodigalgal.xaigateway.admin.api.AdminChatExecuteResponse;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterAction;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterResult;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterRule;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterService;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntime;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeContext;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionStreamResult;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequestMetadata;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailService;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceContentKind;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceDirection;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceStage;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteExecutionAttempt;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.routing.RoutingPolicyRuntimeEnforcementService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesRequest;
import com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesRequestMapper;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentRequest;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentRequestMapper;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionRequest;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionRequestMapper;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesRequestMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Service
@Transactional
public class GatewayChatExecutionService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final GatewayRequestLifecycleService gatewayRequestLifecycleService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final AccountSelectionService accountSelectionService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;
    private final OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper;
    private final OpenAiResponsesRequestMapper openAiResponsesRequestMapper;
    private final AnthropicMessagesRequestMapper anthropicMessagesRequestMapper;
    private final GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper;
    private final List<GatewayChatRuntime> gatewayChatRuntimes;
    private final GatewayProperties gatewayProperties;
    private final RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService;
    private final RoutingPolicyRuntimeEnforcementService routingPolicyRuntimeEnforcementService;
    private final CloudCliRequestFilterService cloudCliRequestFilterService;
    private final GatewayRequestTraceDetailService gatewayRequestTraceDetailService;

    @Autowired
    public GatewayChatExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper,
            OpenAiResponsesRequestMapper openAiResponsesRequestMapper,
            AnthropicMessagesRequestMapper anthropicMessagesRequestMapper,
            GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper,
            List<GatewayChatRuntime> gatewayChatRuntimes,
            GatewayProperties gatewayProperties,
            RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService,
            RoutingPolicyRuntimeEnforcementService routingPolicyRuntimeEnforcementService,
            CloudCliRequestFilterService cloudCliRequestFilterService,
            GatewayRequestTraceDetailService gatewayRequestTraceDetailService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.gatewayRequestLifecycleService = gatewayRequestLifecycleService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.accountSelectionService = accountSelectionService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
        this.openAiChatCompletionRequestMapper = openAiChatCompletionRequestMapper;
        this.openAiResponsesRequestMapper = openAiResponsesRequestMapper;
        this.anthropicMessagesRequestMapper = anthropicMessagesRequestMapper;
        this.geminiGenerateContentRequestMapper = geminiGenerateContentRequestMapper;
        this.gatewayChatRuntimes = gatewayChatRuntimes;
        this.gatewayProperties = gatewayProperties;
        this.routingPolicyRuntimeConfigService = routingPolicyRuntimeConfigService;
        this.routingPolicyRuntimeEnforcementService = routingPolicyRuntimeEnforcementService;
        this.cloudCliRequestFilterService = cloudCliRequestFilterService;
        this.gatewayRequestTraceDetailService = gatewayRequestTraceDetailService;
    }

    public GatewayChatExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper,
            OpenAiResponsesRequestMapper openAiResponsesRequestMapper,
            AnthropicMessagesRequestMapper anthropicMessagesRequestMapper,
            GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper,
            List<GatewayChatRuntime> gatewayChatRuntimes,
            GatewayProperties gatewayProperties,
            RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService,
            RoutingPolicyRuntimeEnforcementService routingPolicyRuntimeEnforcementService) {
        this(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                gatewayObservabilityService,
                gatewayRequestLifecycleService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                credentialMaterialResolver,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                openAiChatCompletionRequestMapper,
                openAiResponsesRequestMapper,
                anthropicMessagesRequestMapper,
                geminiGenerateContentRequestMapper,
                gatewayChatRuntimes,
                gatewayProperties,
                routingPolicyRuntimeConfigService,
                routingPolicyRuntimeEnforcementService,
                new CloudCliRequestFilterService(),
                null
        );
    }

    public GatewayChatExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            OpenAiChatCompletionRequestMapper openAiChatCompletionRequestMapper,
            OpenAiResponsesRequestMapper openAiResponsesRequestMapper,
            AnthropicMessagesRequestMapper anthropicMessagesRequestMapper,
            GeminiGenerateContentRequestMapper geminiGenerateContentRequestMapper,
            List<GatewayChatRuntime> gatewayChatRuntimes,
            GatewayProperties gatewayProperties) {
        this(
                gatewayRouteSelectionService,
                upstreamCredentialRepository,
                credentialCryptoService,
                gatewayObservabilityService,
                gatewayRequestLifecycleService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                credentialMaterialResolver,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                openAiChatCompletionRequestMapper,
                openAiResponsesRequestMapper,
                anthropicMessagesRequestMapper,
                geminiGenerateContentRequestMapper,
                gatewayChatRuntimes,
                gatewayProperties,
                null,
                null,
                new CloudCliRequestFilterService(),
                null
        );
    }

    public AdminChatExecuteResponse execute(AdminChatExecuteRequest request) {
        CanonicalExecutionResult response = executeGatewayResponse(buildAdminRequest(request));
        return new AdminChatExecuteResponse(
                response.requestId(),
                response.routeSelection(),
                response.plan(),
                response.plan().executionBackend(),
                response.response().outputText(),
                toGatewayUsage(response.response().usage()),
                toGatewayToolCalls(response.response().toolCalls())
        );
    }

    public CanonicalExecutionResult executeGatewayResponse(CanonicalRequest request) {
        return executeGatewayResponse(request, GatewayClientFamily.GENERIC_OPENAI);
    }

    public CanonicalExecutionResult executeGatewayResponse(CanonicalRequest request, GatewayClientFamily clientFamily) {
        GatewayClientFamily effectiveClientFamily = clientFamily == null ? GatewayClientFamily.GENERIC_OPENAI : clientFamily;
        CloudCliRequestFilterResult filterResult = applyCloudCliRequestFilters(request, effectiveClientFamily);
        CanonicalRequest filteredRequest = filterResult.request();
        ensureRequestFilterAllowed(filterResult);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        JsonNode routeBody = buildRouteBody(filteredRequest);
        annotateFilterHits(routeBody, filterResult);
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                filteredRequest.distributedKeyPrefix(),
                filteredRequest.ingressProtocol().name().toLowerCase(),
                filteredRequest.requestPath(),
                filteredRequest.requestedModel(),
                routeBody,
                effectiveClientFamily,
                true,
                sessionAffinityKey(filteredRequest),
                "POST"
        ));
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe("POST", filteredRequest.requestPath(), routeBody);
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, filteredRequest, false, startedAt);
        recordTrace(requestId, RequestTraceStage.DOWNSTREAM_REQUEST, RequestTraceDirection.DOWNSTREAM, RequestTraceContentKind.JSON, routeBody,
                traceMetadata(RequestTraceStage.DOWNSTREAM_REQUEST, "protocol", filteredRequest.ingressProtocol().name(), "requestPath", filteredRequest.requestPath()));
        recordTrace(requestId, RequestTraceStage.CANONICAL_REQUEST, RequestTraceDirection.INTERNAL, RequestTraceContentKind.JSON, filteredRequest,
                traceMetadata(RequestTraceStage.CANONICAL_REQUEST, "filterApplied", !filterResult.appliedRuleIds().isEmpty()));

        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = maxFallbackAttempts(selectionResult);
            RuntimeException lastException = null;

            for (int index = 0; index < maxAttempts; index++) {
                RouteCandidateView candidate = selectionResult.candidates().get(index);
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, candidate, attempts);
                CanonicalExecutionPlanCompilation executionPlanCompilation = translationExecutionPlanCompiler.compileSelected(
                        candidateSelection,
                        filteredRequest,
                        semantics,
                        routeBody
                );
                CanonicalExecutionPlan executionPlan = executionPlanCompilation.canonicalPlan();
                recordTrace(requestId, RequestTraceStage.TRANSLATION_PLAN, RequestTraceDirection.INTERNAL, RequestTraceContentKind.JSON, executionPlan,
                        traceMetadata(RequestTraceStage.TRANSLATION_PLAN, "candidateIndex", index + 1, "providerType", candidate.candidate().providerType().name()));
                UpstreamCredentialEntity credential = null;
                ResolvedCredentialMaterial credentialMaterial = null;
                try {
                    ensureExecutable(executionPlan);
                    credential = getRequiredCredential(candidate.candidate().credentialId());
                    credentialMaterial = credentialMaterialResolver.resolve(candidateSelection, credential);
                    GatewayChatRuntime runtime = resolveRuntime(candidate.candidate(), executionPlan.executionBackend());
                    recordTrace(requestId, RequestTraceStage.UPSTREAM_REQUEST, RequestTraceDirection.UPSTREAM, RequestTraceContentKind.JSON,
                            upstreamChatRequestSnapshot(candidateSelection, filteredRequest, executionPlan),
                            traceMetadata(RequestTraceStage.UPSTREAM_REQUEST, "credentialId", candidate.candidate().credentialId(), "backend", backendName(executionPlan)));
                    CanonicalResponse result = runtime.execute(new GatewayChatRuntimeContext(
                            candidateSelection,
                            credential,
                            credentialMaterial,
                            filteredRequest,
                            executionPlan
                    ));
                    if (isEmptyCanonicalResult(result)) {
                        throw new IllegalStateException("上游响应为空。");
                    }
                    recordTrace(requestId, RequestTraceStage.UPSTREAM_RESPONSE, RequestTraceDirection.UPSTREAM, RequestTraceContentKind.JSON, result,
                            traceMetadata(RequestTraceStage.UPSTREAM_RESPONSE, "providerType", candidate.candidate().providerType().name()));

                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidate.candidate().credentialId(),
                            candidate.candidate().providerType().name(),
                            "SUCCEEDED",
                            candidateSelection.selectionSource().name()
                    ));
                    RouteSelectionResult finalSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                    gatewayRouteSelectionService.recordSuccessfulSelection(finalSelection);
                    recordRoutingPolicySuccess(candidate, credential);
                    gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);

                    CanonicalResponse enriched = enrichResponse(requestId, finalSelection, result);
                    GatewayUsageView usageView = toUsageView(
                            enriched.usage(),
                            GatewayUsageCompleteness.FINAL,
                            GatewayUsageSource.DIRECT_RESPONSE
                    );
                    if (enriched.usage() != null && enriched.usage().present()) {
                        gatewayObservabilityService.recordCacheUsage(
                                requestId,
                                finalSelection,
                                toGatewayUsage(enriched.usage()),
                                cacheKind(usageView),
                                usageView.cachedContentRef()
                        );
                    }
                    gatewayRequestLifecycleService.completeRequest(
                            requestId,
                            finalSelection,
                            filteredRequest,
                            false,
                            usageView,
                            startedAt,
                            credentialMaterial.accountId(),
                            null
                    );
                    recordTrace(requestId, RequestTraceStage.DOWNSTREAM_RESPONSE, RequestTraceDirection.DOWNSTREAM, RequestTraceContentKind.JSON, downstreamChatResponseSnapshot(enriched),
                            traceMetadata(RequestTraceStage.DOWNSTREAM_RESPONSE, "status", "COMPLETED", "stream", false));
                    return new CanonicalExecutionResult(requestId, finalSelection, executionPlan, enriched);
                } catch (RuntimeException exception) {
                    recordTraceError(requestId, RequestTraceStage.ERROR, RequestTraceDirection.INTERNAL, exception,
                            traceMetadata(RequestTraceStage.ERROR, "candidateIndex", index + 1, "providerType", candidate.candidate().providerType().name()));
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidate.candidate().credentialId(),
                            candidate.candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            fallbackDetail(exception)
                    ));
                    recordCandidateFailure(candidateSelection, candidate, credential, exception);
                    lastException = exception;
                    if (!shouldFallback(exception) || index == maxAttempts - 1) {
                        RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(requestId, failedSelection);
                        gatewayRequestLifecycleService.failRequest(
                                requestId,
                                failedSelection,
                                filteredRequest,
                                false,
                                exception,
                                GatewayUsageView.empty(),
                                startedAt,
                                credentialMaterial == null ? null : credentialMaterial.accountId(),
                                null
                        );
                        throw exception;
                    }
                }
            }
            if (lastException != null) {
                throw lastException;
            }
            throw new IllegalStateException("当前 provider 候选没有可用执行尝试。");
        } catch (RuntimeException exception) {
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey());
        }
    }

    public CanonicalExecutionStreamResult executeGatewayStream(CanonicalRequest request) {
        return executeGatewayStream(request, GatewayClientFamily.GENERIC_OPENAI);
    }

    public CanonicalExecutionStreamResult executeGatewayStream(CanonicalRequest request, GatewayClientFamily clientFamily) {
        GatewayClientFamily effectiveClientFamily = clientFamily == null ? GatewayClientFamily.GENERIC_OPENAI : clientFamily;
        CloudCliRequestFilterResult filterResult = applyCloudCliRequestFilters(request, effectiveClientFamily);
        CanonicalRequest filteredRequest = filterResult.request();
        ensureRequestFilterAllowed(filterResult);
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        JsonNode routeBody = buildRouteBody(filteredRequest);
        annotateFilterHits(routeBody, filterResult);
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                filteredRequest.distributedKeyPrefix(),
                filteredRequest.ingressProtocol().name().toLowerCase(),
                filteredRequest.requestPath(),
                filteredRequest.requestedModel(),
                routeBody,
                effectiveClientFamily,
                true,
                sessionAffinityKey(filteredRequest),
                "POST"
        ));
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe("POST", filteredRequest.requestPath(), routeBody);
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, filteredRequest, true, startedAt);
        recordTrace(requestId, RequestTraceStage.DOWNSTREAM_REQUEST, RequestTraceDirection.DOWNSTREAM, RequestTraceContentKind.JSON, routeBody,
                traceMetadata(RequestTraceStage.DOWNSTREAM_REQUEST, "protocol", filteredRequest.ingressProtocol().name(), "requestPath", filteredRequest.requestPath(), "stream", true));
        recordTrace(requestId, RequestTraceStage.CANONICAL_REQUEST, RequestTraceDirection.INTERNAL, RequestTraceContentKind.JSON, filteredRequest,
                traceMetadata(RequestTraceStage.CANONICAL_REQUEST, "filterApplied", !filterResult.appliedRuleIds().isEmpty(), "stream", true));
        AtomicReference<CanonicalUsage> lastVisibleUsage = new AtomicReference<>(CanonicalUsage.empty());
        AtomicBoolean terminalRecorded = new AtomicBoolean(false);
        AtomicReference<RouteSelectionResult> finalSelectionRef = new AtomicReference<>(selectionResult);
        AtomicReference<CanonicalExecutionPlan> planRef = new AtomicReference<>();
        AtomicReference<Long> selectedAccountIdRef = new AtomicReference<>(null);
        AtomicReference<Long> firstTokenLatencyMsRef = new AtomicReference<>(null);
        List<RouteExecutionAttempt> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        int maxAttempts = maxFallbackAttempts(selectionResult);
        Flux<CanonicalStreamEvent> chunks = streamAttempt(
                        requestId,
                        selectionResult,
                        filteredRequest,
                        routeBody,
                        semantics,
                        0,
                        maxAttempts,
                        attempts,
                        finalSelectionRef,
                        planRef,
                        selectedAccountIdRef,
                        firstTokenLatencyMsRef,
                        startedAt
                )
                .doOnNext(event -> {
                    if (event.usage() != null && event.usage().present()) {
                        lastVisibleUsage.set(event.usage());
                    }
                    if (event.terminal()) {
                        GatewayUsageView usageView = terminalUsageView(event.usage(), lastVisibleUsage.get());
                        RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                        recordTerminalUsage(
                                requestId,
                                finalSelection,
                                filteredRequest,
                                startedAt,
                                usageView,
                                event.usage(),
                                lastVisibleUsage.get(),
                                selectedAccountIdRef.get(),
                                firstTokenLatencyMsRef.get()
                        );
                        terminalRecorded.set(true);
                    }
                })
                .doOnComplete(() -> {
                    if (!terminalRecorded.get()) {
                        GatewayUsageView usageView = terminalUsageView(null, lastVisibleUsage.get());
                        RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                        recordTerminalUsage(
                                requestId,
                                finalSelection,
                                filteredRequest,
                                startedAt,
                                usageView,
                                null,
                                lastVisibleUsage.get(),
                                selectedAccountIdRef.get(),
                                firstTokenLatencyMsRef.get()
                        );
                    }
                })
                .doOnError(error -> {
                    RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                    gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                    gatewayRouteSelectionService.invalidateSelection(finalSelection);
                    gatewayRequestLifecycleService.failRequest(
                            requestId,
                            finalSelection,
                            filteredRequest,
                            true,
                            error,
                            terminalUsageView(null, lastVisibleUsage.get()),
                            startedAt,
                            selectedAccountIdRef.get(),
                            firstTokenLatencyMsRef.get()
                    );
                    recordTraceError(requestId, RequestTraceStage.ERROR, RequestTraceDirection.INTERNAL, error,
                            traceMetadata(RequestTraceStage.ERROR, "status", "FAILED", "stream", true));
                })
                .doOnCancel(() -> {
                    RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                    gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                    gatewayRouteSelectionService.invalidateSelection(finalSelection);
                    gatewayRequestLifecycleService.cancelRequest(
                            requestId,
                            finalSelection,
                            filteredRequest,
                            true,
                            terminalUsageView(null, lastVisibleUsage.get()),
                            startedAt,
                            selectedAccountIdRef.get(),
                            firstTokenLatencyMsRef.get()
                    );
                })
                .doFinally(signalType -> distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey()));

        return new CanonicalExecutionStreamResult(requestId, selectionResult, planRef.get(), chunks);
    }

    private Flux<CanonicalStreamEvent> streamAttempt(
            String requestId,
            RouteSelectionResult baseSelection,
            CanonicalRequest request,
            JsonNode routeBody,
            GatewayRequestSemantics semantics,
            int candidateIndex,
            int maxAttempts,
            List<RouteExecutionAttempt> attempts,
            AtomicReference<RouteSelectionResult> finalSelectionRef,
            AtomicReference<CanonicalExecutionPlan> planRef,
            AtomicReference<Long> selectedAccountIdRef,
            AtomicReference<Long> firstTokenLatencyMsRef,
            Instant startedAt) {
        RouteCandidateView candidate = baseSelection.candidates().get(candidateIndex);
        RouteSelectionResult candidateSelection = selectionForCandidate(baseSelection, candidate, attempts);
        finalSelectionRef.set(candidateSelection);

        CanonicalExecutionPlanCompilation executionPlanCompilation = translationExecutionPlanCompiler.compileSelected(
                candidateSelection,
                request,
                semantics,
                routeBody
        );
        CanonicalExecutionPlan executionPlan = ensureExecutable(executionPlanCompilation.canonicalPlan());
        planRef.set(executionPlan);
        recordTrace(requestId, RequestTraceStage.TRANSLATION_PLAN, RequestTraceDirection.INTERNAL, RequestTraceContentKind.JSON, executionPlan,
                traceMetadata(RequestTraceStage.TRANSLATION_PLAN, "candidateIndex", candidateIndex + 1, "providerType", candidate.candidate().providerType().name(), "stream", true));
        UpstreamCredentialEntity credential = getRequiredCredential(candidate.candidate().credentialId());
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(candidateSelection, credential);
        selectedAccountIdRef.set(credentialMaterial.accountId());
        GatewayChatRuntime runtime = resolveRuntime(candidate.candidate(), executionPlan.executionBackend());
        recordTrace(requestId, RequestTraceStage.UPSTREAM_REQUEST, RequestTraceDirection.UPSTREAM, RequestTraceContentKind.JSON,
                upstreamChatRequestSnapshot(candidateSelection, request, executionPlan),
                traceMetadata(RequestTraceStage.UPSTREAM_REQUEST, "credentialId", candidate.candidate().credentialId(), "backend", backendName(executionPlan), "stream", true));
        AtomicBoolean firstOutputCommitted = new AtomicBoolean(false);
        AtomicBoolean successRecorded = new AtomicBoolean(false);

        return runtime.executeStream(new GatewayChatRuntimeContext(
                        candidateSelection,
                        credential,
                        credentialMaterial,
                        request,
                        executionPlan
                ))
                .doOnNext(event -> {
                    if (isVisibleStreamEvent(event)) {
                        firstTokenLatencyMsRef.compareAndSet(null, Duration.between(startedAt, Instant.now()).toMillis());
                        firstOutputCommitted.set(true);
                    }
                    if (event.terminal() && successRecorded.compareAndSet(false, true)) {
                        recordTrace(requestId, RequestTraceStage.UPSTREAM_RESPONSE, RequestTraceDirection.UPSTREAM, RequestTraceContentKind.JSON, event,
                                traceMetadata(RequestTraceStage.UPSTREAM_RESPONSE, "providerType", candidate.candidate().providerType().name(), "stream", true, "terminal", true));
                        attempts.add(new RouteExecutionAttempt(
                                candidateIndex + 1,
                                candidate.candidate().credentialId(),
                                candidate.candidate().providerType().name(),
                                "SUCCEEDED",
                                candidateSelection.selectionSource().name()
                        ));
                        RouteSelectionResult finalSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        finalSelectionRef.set(finalSelection);
                        gatewayRouteSelectionService.recordSuccessfulSelection(finalSelection);
                        recordRoutingPolicySuccess(candidate, credential);
                    }
                })
                .doOnComplete(() -> {
                    if (successRecorded.compareAndSet(false, true)) {
                        attempts.add(new RouteExecutionAttempt(
                                candidateIndex + 1,
                                candidate.candidate().credentialId(),
                                candidate.candidate().providerType().name(),
                                "SUCCEEDED",
                                candidateSelection.selectionSource().name()
                        ));
                        RouteSelectionResult finalSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        finalSelectionRef.set(finalSelection);
                        gatewayRouteSelectionService.recordSuccessfulSelection(finalSelection);
                        recordRoutingPolicySuccess(candidate, credential);
                    }
                    recordTrace(requestId, RequestTraceStage.DOWNSTREAM_RESPONSE, RequestTraceDirection.DOWNSTREAM, RequestTraceContentKind.JSON,
                            traceMetadata(RequestTraceStage.DOWNSTREAM_RESPONSE, "streamCompleted", true, "firstTokenLatencyMs", firstTokenLatencyMsRef.get()),
                            traceMetadata(RequestTraceStage.DOWNSTREAM_RESPONSE, "status", "COMPLETED", "stream", true));
                })
                .onErrorResume(error -> {
                    recordTraceError(requestId, RequestTraceStage.ERROR, RequestTraceDirection.UPSTREAM, error,
                            traceMetadata(RequestTraceStage.ERROR, "candidateIndex", candidateIndex + 1, "stream", true));
                    String outcome = firstOutputCommitted.get() ? "FAILED_AFTER_FIRST_BYTE" : "FAILED_BEFORE_FIRST_BYTE";
                    attempts.add(new RouteExecutionAttempt(
                            candidateIndex + 1,
                            candidate.candidate().credentialId(),
                            candidate.candidate().providerType().name(),
                            outcome,
                            fallbackDetail(error)
                    ));
                    RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                    finalSelectionRef.set(failedSelection);
                    recordCandidateFailure(candidateSelection, candidate, credential, error);
                    if (!firstOutputCommitted.get()
                            && shouldFallback(error)
                            && candidateIndex + 1 < maxAttempts
                            && candidateIndex + 1 < baseSelection.candidates().size()) {
                        return streamAttempt(
                                requestId,
                                baseSelection,
                                request,
                                routeBody,
                                semantics,
                                candidateIndex + 1,
                                maxAttempts,
                                attempts,
                                finalSelectionRef,
                                planRef,
                                selectedAccountIdRef,
                                firstTokenLatencyMsRef,
                                startedAt
                        );
                    }
                    return Flux.error(error);
                });
    }

    private void recordTerminalUsage(
            String requestId,
            RouteSelectionResult selectionResult,
            CanonicalRequest request,
            Instant startedAt,
            GatewayUsageView usageView,
            CanonicalUsage terminalUsage,
            CanonicalUsage lastVisibleUsage,
            Long accountId,
            Long firstTokenLatencyMs) {
        GatewayUsage usageForLog = terminalUsage != null && terminalUsage.present()
                ? toGatewayUsage(terminalUsage)
                : toGatewayUsage(lastVisibleUsage);
        if (usageForLog != null && !usageForLog.isEmpty()) {
            gatewayObservabilityService.recordCacheUsage(
                    requestId,
                    selectionResult,
                    usageForLog,
                    cacheKind(usageView),
                    usageView.cachedContentRef()
            );
        }
        gatewayRequestLifecycleService.completeRequest(
                requestId,
                selectionResult,
                request,
                true,
                usageView,
                startedAt,
                accountId,
                firstTokenLatencyMs
        );
    }

    private GatewayUsageView terminalUsageView(CanonicalUsage terminalUsage, CanonicalUsage lastVisibleUsage) {
        if (terminalUsage != null && terminalUsage.present()) {
            return toUsageView(
                    terminalUsage,
                    GatewayUsageCompleteness.FINAL,
                    GatewayUsageSource.PROVIDER_FINAL
            );
        }
        if (lastVisibleUsage != null && lastVisibleUsage.present()) {
            return toUsageView(
                    lastVisibleUsage,
                    GatewayUsageCompleteness.PARTIAL,
                    GatewayUsageSource.LAST_VISIBLE
            );
        }
        return GatewayUsageView.empty();
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
        gatewayRequestTraceDetailService.record(
                requestId,
                stage,
                direction,
                contentKind,
                payload,
                metadata == null ? Map.of() : metadata
        );
    }

    private void recordTraceError(
            String requestId,
            RequestTraceStage stage,
            RequestTraceDirection direction,
            Throwable error,
            Map<String, ?> metadata) {
        if (gatewayRequestTraceDetailService == null) {
            return;
        }
        gatewayRequestTraceDetailService.recordError(requestId, stage, direction, error, metadata == null ? Map.of() : metadata);
    }

    private Map<String, Object> upstreamChatRequestSnapshot(
            RouteSelectionResult selectionResult,
            CanonicalRequest request,
            CanonicalExecutionPlan executionPlan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (selectionResult != null) {
            putIfPresent(snapshot, "distributedKeyId", selectionResult.distributedKeyId());
            putIfPresent(snapshot, "protocol", selectionResult.protocol());
            putIfPresent(snapshot, "providerType", selectionResult.selectedCandidate() == null
                    ? null
                    : selectionResult.selectedCandidate().candidate().providerType());
            putIfPresent(snapshot, "credentialId", selectionResult.selectedCandidate() == null
                    ? null
                    : selectionResult.selectedCandidate().candidate().credentialId());
            putIfPresent(snapshot, "credentialName", selectionResult.selectedCandidate() == null
                    ? null
                    : selectionResult.selectedCandidate().candidate().credentialName());
            putIfPresent(snapshot, "baseUrl", selectionResult.selectedCandidate() == null
                    ? null
                    : selectionResult.selectedCandidate().candidate().baseUrl());
            putIfPresent(snapshot, "selectionSource", selectionResult.selectionSource());
        }
        if (request != null) {
            putIfPresent(snapshot, "requestPath", request.requestPath());
            putIfPresent(snapshot, "requestedModel", request.requestedModel());
            putIfPresent(snapshot, "messageCount", request.messages() == null ? 0 : request.messages().size());
            putIfPresent(snapshot, "toolCount", request.tools() == null ? 0 : request.tools().size());
            putIfPresent(snapshot, "temperature", request.temperature());
            putIfPresent(snapshot, "maxTokens", request.maxTokens());
            putIfPresent(snapshot, "hasReasoning", request.reasoning() != null);
            putIfPresent(snapshot, "hasProviderExtensions", request.providerExtensions() != null && !request.providerExtensions().isNull());
        }
        if (executionPlan != null) {
            putIfPresent(snapshot, "executionBackend", backendName(executionPlan));
            putIfPresent(snapshot, "executionKind", executionPlan.executionKind());
            putIfPresent(snapshot, "resourceType", executionPlan.resourceType());
            putIfPresent(snapshot, "operation", executionPlan.operation());
            putIfPresent(snapshot, "supportStatus", executionPlan.supportStatus());
            putIfPresent(snapshot, "degradationLevel", executionPlan.degradationLevel());
            putIfPresent(snapshot, "routeSelectionMode", executionPlan.routeSelectionMode());
        }
        return snapshot;
    }

    private Map<String, Object> downstreamChatResponseSnapshot(CanonicalResponse response) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (response == null) {
            return snapshot;
        }
        putIfPresent(snapshot, "requestId", response.requestId());
        putIfPresent(snapshot, "publicModel", response.publicModel());
        putIfPresent(snapshot, "finishReason", response.finishReason());
        putIfPresent(snapshot, "outputText", response.outputText());
        putIfPresent(snapshot, "reasoning", response.reasoning());
        putIfPresent(snapshot, "toolCalls", response.toolCalls());
        putIfPresent(snapshot, "usage", response.usage());
        putIfPresent(snapshot, "hasRawResponse", response.rawResponse() != null && !response.rawResponse().isNull());
        return snapshot;
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
            return "gateway_trace_snapshot";
        }
        return switch (stage) {
            case DOWNSTREAM_REQUEST -> "downstream_parsed_request_body";
            case CANONICAL_REQUEST -> "gateway_canonical_request_model";
            case TRANSLATION_PLAN -> "gateway_translation_execution_plan";
            case UPSTREAM_REQUEST -> "gateway_constructed_upstream_request_summary";
            case UPSTREAM_RESPONSE -> "gateway_runtime_sdk_response_summary";
            case DOWNSTREAM_RESPONSE -> "gateway_downstream_response_summary";
            case ERROR -> "gateway_error_summary";
            case CUSTOM -> "gateway_custom_trace_snapshot";
        };
    }

    private String wireBodyLimitation(RequestTraceStage stage) {
        if (stage == RequestTraceStage.DOWNSTREAM_REQUEST) {
            return "parsed downstream body after gateway ingress handling; sensitive fields may be redacted";
        }
        if (stage == RequestTraceStage.UPSTREAM_REQUEST || stage == RequestTraceStage.UPSTREAM_RESPONSE) {
            return "structured gateway/runtime summary, not raw upstream HTTP wire body";
        }
        return "structured gateway snapshot, not raw HTTP wire body";
    }

    private String backendName(CanonicalExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.executionBackend() == null) {
            return null;
        }
        return executionPlan.executionBackend().name();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private GatewayUsageView toUsageView(
            CanonicalUsage usage,
            GatewayUsageCompleteness completeness,
            GatewayUsageSource source) {
        if (usage == null || !usage.present()) {
            return GatewayUsageView.empty();
        }
        return new GatewayUsageView(
                usage.promptTokens(),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.reasoningTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                Math.max(usage.promptTokens() - usage.cacheWriteTokens(), 0),
                null,
                usage.totalTokens(),
                completeness,
                source,
                null
        );
    }

    private GatewayUsage toGatewayUsage(CanonicalUsage usage) {
        if (usage == null || !usage.present()) {
            return GatewayUsage.empty();
        }
        return new GatewayUsage(
                usage.promptTokens(),
                usage.promptTokens(),
                usage.completionTokens(),
                usage.reasoningTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                null,
                usage.totalTokens(),
                null
        );
    }

    private List<GatewayToolCall> toGatewayToolCalls(List<CanonicalToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .map(toolCall -> new GatewayToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                .toList();
    }

    private CanonicalResponse enrichResponse(String requestId, RouteSelectionResult selectionResult, CanonicalResponse result) {
        return new CanonicalResponse(
                requestId,
                selectionResult.publicModel(),
                result.outputText(),
                result.reasoning(),
                result.toolCalls(),
                result.usage(),
                result.finishReason(),
                result.rawResponse()
        );
    }

    private CanonicalRequest buildAdminRequest(AdminChatExecuteRequest request) {
        if (request.body() == null || request.body().isNull()) {
            throw new IllegalArgumentException("admin chat execute 缺少 body。");
        }
        return switch (request.requestPath()) {
            case "/v1/chat/completions" -> openAiChatCompletionRequestMapper.toCanonicalRequest(
                    request.distributedKeyPrefix(),
                    request.body()
            );
            case "/v1/responses" -> openAiResponsesRequestMapper.toCanonicalRequest(
                    request.distributedKeyPrefix(),
                    request.body()
            );
            case "/v1/messages" -> anthropicMessagesRequestMapper.toCanonicalRequest(
                    request.distributedKeyPrefix(),
                    request.body()
            );
            default -> {
                if (request.requestPath().contains(":generateContent")) {
                    yield geminiGenerateContentRequestMapper.toCanonicalRequest(
                            request.distributedKeyPrefix(),
                            request.requestedModel(),
                            request.body(),
                            false
                    );
                }
                if (request.requestPath().contains(":streamGenerateContent")) {
                    yield geminiGenerateContentRequestMapper.toCanonicalRequest(
                            request.distributedKeyPrefix(),
                            request.requestedModel(),
                            request.body(),
                            true
                    );
                }
                throw new IllegalArgumentException("当前 admin chat execute 暂不支持该 requestPath：" + request.requestPath());
            }
        };
    }

    private String cacheKind(GatewayUsageView usageView) {
        if (usageView == null || !usageView.present()) {
            return "none";
        }
        if (usageView.cachedContentRef() != null && !usageView.cachedContentRef().isBlank()) {
            return "cached_content";
        }
        if (usageView.cacheHitTokens() > 0 || usageView.cacheWriteTokens() > 0) {
            return "prompt_cache";
        }
        return "none";
    }

    private CloudCliRequestFilterResult applyCloudCliRequestFilters(
            CanonicalRequest request,
            GatewayClientFamily clientFamily) {
        GatewayProperties.Cli.RequestFilter filter = gatewayProperties.getCli().getRequestFilter();
        if (filter == null || !filter.isEnabled() || filter.getRules() == null || filter.getRules().isEmpty()) {
            return new CloudCliRequestFilterResult(request, List.of(), List.of());
        }
        List<CloudCliRequestFilterRule> rules = filter.getRules().stream()
                .map(this::toCloudCliRequestFilterRule)
                .toList();
        return cloudCliRequestFilterService.apply(request, clientFamily, rules);
    }

    private CloudCliRequestFilterRule toCloudCliRequestFilterRule(GatewayProperties.Cli.Rule rule) {
        CloudCliRequestFilterAction action = null;
        if (rule.getAction() != null && !rule.getAction().isBlank()) {
            try {
                action = CloudCliRequestFilterAction.valueOf(
                        rule.getAction().trim().toUpperCase(Locale.ROOT).replace('-', '_')
                );
            } catch (IllegalArgumentException ignored) {
                action = null;
            }
        }
        return new CloudCliRequestFilterRule(
                rule.getId(),
                action,
                rule.getClientFamilies(),
                rule.getRole(),
                rule.getContains(),
                rule.getReplacement(),
                rule.getTarget(),
                rule.getPath()
        );
    }

    private void ensureRequestFilterAllowed(CloudCliRequestFilterResult filterResult) {
        if (filterResult != null && filterResult.denied()) {
            throw new IllegalArgumentException("请求被云端 Request Filter 拒绝：" + filterResult.denyRuleId());
        }
    }

    private void annotateFilterHits(JsonNode routeBody, CloudCliRequestFilterResult filterResult) {
        if (!(routeBody instanceof ObjectNode root) || filterResult == null) {
            return;
        }
        if (filterResult.appliedRuleIds().isEmpty() && filterResult.skippedRuleIds().isEmpty()) {
            return;
        }
        ObjectNode metadata = root.putObject("x_ai_gateway_filter");
        var applied = metadata.putArray("applied_rule_ids");
        filterResult.appliedRuleIds().forEach(applied::add);
        var skipped = metadata.putArray("skipped_rule_ids");
        filterResult.skippedRuleIds().forEach(skipped::add);
        var hits = metadata.putArray("hits");
        filterResult.hits().forEach(hit -> hits.addObject()
                .put("rule_id", hit.ruleId())
                .put("action", hit.action())
                .put("target", hit.target())
                .put("path", hit.path())
                .put("summary", hit.summary()));
        metadata.put("denied", filterResult.denied());
        if (filterResult.denyRuleId() != null) {
            metadata.put("deny_rule_id", filterResult.denyRuleId());
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

    private String fallbackDetail(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable == null ? "fallback" : throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }

    private boolean isEmptyCanonicalResult(CanonicalResponse result) {
        if (result == null) {
            return true;
        }
        boolean hasText = result.outputText() != null && !result.outputText().isBlank();
        boolean hasReasoning = result.reasoning() != null && !result.reasoning().isBlank();
        boolean hasToolCalls = result.toolCalls() != null && !result.toolCalls().isEmpty();
        return !hasText && !hasReasoning && !hasToolCalls;
    }

    private int maxFallbackAttempts(RouteSelectionResult selectionResult) {
        int candidates = selectionResult.candidates().size();
        int defaultMaxAttempts = gatewayProperties.getRouting().getMaxFallbackAttempts();
        if (routingPolicyRuntimeConfigService == null) {
            return Math.min(candidates, defaultMaxAttempts);
        }
        return routingPolicyRuntimeConfigService.maxAttempts(defaultMaxAttempts, candidates);
    }

    private void recordRoutingPolicySuccess(RouteCandidateView candidate, UpstreamCredentialEntity credential) {
        if (routingPolicyRuntimeEnforcementService != null) {
            routingPolicyRuntimeEnforcementService.recordSuccess(candidate, credential);
        }
    }

    private void recordRoutingPolicyFailure(RouteCandidateView candidate, UpstreamCredentialEntity credential, String reason) {
        if (routingPolicyRuntimeEnforcementService != null) {
            routingPolicyRuntimeEnforcementService.recordFailure(candidate, credential, reason);
        }
    }

    private CanonicalExecutionPlan ensureExecutable(CanonicalExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.executable()) {
            return executionPlan;
        }
        if (executionPlan.blockerReasons() != null && !executionPlan.blockerReasons().isEmpty()) {
            throw new BlockedExecutionPlanException(executionPlan, String.join("；", executionPlan.blockerReasons()));
        }
        throw new BlockedExecutionPlanException(executionPlan, "当前请求在 planner 阶段被阻止执行。");
    }

    private void recordCandidateFailure(
            RouteSelectionResult candidateSelection,
            RouteCandidateView candidate,
            UpstreamCredentialEntity credential,
            Throwable throwable) {
        if (throwable instanceof BlockedExecutionPlanException) {
            return;
        }
        gatewayRouteSelectionService.invalidateSelection(candidateSelection);
        gatewayRouteSelectionService.markCredentialCooldown(candidate.candidate().credentialId(), fallbackDetail(throwable));
        recordRoutingPolicyFailure(candidate, credential, fallbackDetail(throwable));
    }

    private static class BlockedExecutionPlanException extends IllegalArgumentException {
        private final CanonicalExecutionPlan executionPlan;

        private BlockedExecutionPlanException(CanonicalExecutionPlan executionPlan, String message) {
            super(message);
            this.executionPlan = executionPlan;
        }

        @SuppressWarnings("unused")
        private CanonicalExecutionPlan executionPlan() {
            return executionPlan;
        }
    }

    private boolean isVisibleStreamEvent(CanonicalStreamEvent event) {
        if (event == null) {
            return false;
        }
        return (event.textDelta() != null && !event.textDelta().isBlank())
                || (event.reasoningDelta() != null && !event.reasoningDelta().isBlank())
                || (event.toolCalls() != null && !event.toolCalls().isEmpty())
                || (event.rawSsePayload() != null && !event.rawSsePayload().isBlank())
                || event.terminal();
    }

    private GatewayChatRuntime resolveRuntime(
            com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate,
            ExecutionBackend backend) {
        return gatewayChatRuntimes.stream()
                .filter(runtime -> runtime.backend() == backend)
                .filter(runtime -> runtime.supports(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到匹配的聊天运行时：" + candidate.providerType() + " / " + backend));
    }

    private JsonNode buildRouteBody(CanonicalRequest canonicalRequest) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("model", canonicalRequest.requestedModel());

        switch (canonicalRequest.ingressProtocol()) {
            case OPENAI, RESPONSES -> writeOpenAiMessages(root, canonicalRequest.messages());
            case ANTHROPIC_NATIVE -> writeAnthropicMessages(root, canonicalRequest.messages());
            case GOOGLE_NATIVE -> writeGeminiMessages(root, canonicalRequest.messages());
            case UNKNOWN -> root.put("prompt", lastUserMessage(canonicalRequest.messages()));
        }

        if (canonicalRequest.tools() != null && !canonicalRequest.tools().isEmpty()) {
            var tools = root.putArray("tools");
            for (CanonicalToolDefinition tool : canonicalRequest.tools()) {
                var node = tools.addObject();
                node.put("type", "function");
                var function = node.putObject("function");
                function.put("name", tool.name());
                if (tool.description() != null) {
                    function.put("description", tool.description());
                }
                if (tool.inputSchema() != null) {
                    function.set("parameters", tool.inputSchema());
                }
                if (tool.strict() != null) {
                    function.put("strict", tool.strict());
                }
            }
        }

        if (canonicalRequest.toolChoice() != null) {
            root.set("tool_choice", canonicalRequest.toolChoice());
        }

        if (canonicalRequest.reasoning() != null) {
            JsonNode reasoning = canonicalRequest.reasoning().rawSettings();
            if (reasoning != null && !reasoning.isNull()) {
                root.set("reasoning", reasoning);
            }
            String reasoningEffort = canonicalRequest.reasoning().effort();
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                root.put("reasoning_effort", reasoningEffort);
            }
        }

        writeIngressMetadata(root, canonicalRequest.metadata());
        return root;
    }

    private void writeIngressMetadata(ObjectNode root, CanonicalRequestMetadata metadata) {
        if (metadata == null) {
            return;
        }
        ObjectNode ingress = root.putObject("x_ai_gateway_ingress");
        putText(ingress, "client_family", metadata.clientFamily());
        putText(ingress, "client_instance", metadata.clientInstance());
        putText(ingress, "workspace_hint", metadata.workspaceHint());
        putText(ingress, "session_affinity_source", metadata.sessionAffinitySource());
        putText(ingress, "session_affinity_key", metadata.sessionAffinityKey());
        ObjectNode headers = ingress.putObject("headers");
        putText(headers, "openai_beta", metadata.openAiBeta());
        putText(headers, "originator", metadata.originator());
        putText(headers, "user_agent", metadata.userAgent());
    }

    private void putText(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private String sessionAffinityKey(CanonicalRequest request) {
        return request == null || request.metadata() == null ? null : request.metadata().sessionAffinityKey();
    }


    private void writeOpenAiMessages(ObjectNode root, List<CanonicalMessage> messages) {
        var array = root.putArray("messages");
        for (CanonicalMessage message : messages) {
            String text = joinText(message);
            List<CanonicalContentPart> mediaParts = mediaParts(message);
            boolean hasText = text != null && !text.isBlank();
            boolean hasMedia = !mediaParts.isEmpty();
            if (!hasText && !hasMedia) {
                continue;
            }
            var item = array.addObject().put("role", normalizeRole(message.role()));
            if (hasMedia) {
                var contentArray = item.putArray("content");
                if (hasText) {
                    contentArray.addObject()
                            .put("type", "text")
                            .put("text", text);
                }
                for (CanonicalContentPart media : mediaParts) {
                    if (media.type() == CanonicalPartType.FILE) {
                        var inputFile = contentArray.addObject()
                                .put("type", "input_file")
                                .putObject("input_file");
                        if (media.uri().startsWith("gateway://")) {
                            inputFile.put("file_id", media.uri().substring("gateway://".length()));
                        } else {
                            inputFile.put("url", media.uri());
                        }
                        if (media.mimeType() != null && !media.mimeType().isBlank()) {
                            inputFile.put("mime_type", media.mimeType());
                        }
                        if (media.name() != null && !media.name().isBlank()) {
                            inputFile.put("filename", media.name());
                        }
                    } else {
                        contentArray.addObject()
                                .put("type", "image_url")
                            .putObject("image_url")
                            .put("url", media.uri());
                    }
                }
            } else {
                item.put("content", text);
            }
        }
    }

    private void writeAnthropicMessages(ObjectNode root, List<CanonicalMessage> messages) {
        String systemPrompt = messages.stream()
                .filter(message -> message.role() == CanonicalMessageRole.SYSTEM)
                .map(this::joinText)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
        root.put("system", systemPrompt);

        var array = root.putArray("messages");
        for (CanonicalMessage message : messages) {
            String role = normalizeRole(message.role());
            String text = joinText(message);
            List<CanonicalContentPart> mediaParts = mediaParts(message);
            boolean hasText = text != null && !text.isBlank();
            boolean hasMedia = !mediaParts.isEmpty();
            if ("system".equals(role) || (!hasText && !hasMedia && message.role() != CanonicalMessageRole.TOOL)) {
                continue;
            }
            if ("tool".equals(role)) {
                CanonicalContentPart toolResult = toolResult(message);
                var content = JsonNodeFactory.instance.arrayNode();
                content.addObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", toolResult == null || toolResult.toolCallId() == null ? "tool-use" : toolResult.toolCallId())
                        .put("content", toolResult == null || toolResult.text() == null ? "" : toolResult.text());
                array.addObject()
                        .put("role", "user")
                        .set("content", content);
            } else {
                var item = array.addObject().put("role", "assistant".equals(role) ? "assistant" : "user");
                if (hasMedia) {
                    var content = JsonNodeFactory.instance.arrayNode();
                    if (hasText) {
                        content.addObject()
                                .put("type", "text")
                                .put("text", text);
                    }
                    for (CanonicalContentPart media : mediaParts) {
                        if (media.type() == CanonicalPartType.FILE) {
                            var block = content.addObject()
                                    .put("type", "document")
                                    .put("title", media.name() == null ? "document" : media.name());
                            block.putObject("source")
                                    .put("type", media.uri().startsWith("gateway://") ? "file_id" : "url")
                                    .put(media.uri().startsWith("gateway://") ? "file_id" : "url",
                                            media.uri().startsWith("gateway://") ? media.uri().substring("gateway://".length()) : media.uri())
                                    .put("media_type", media.mimeType() == null ? "application/octet-stream" : media.mimeType());
                        } else {
                            var block = content.addObject().put("type", "image");
                            block.putObject("source")
                                    .put("type", media.uri().startsWith("gateway://") ? "file_id" : "url")
                                    .put(media.uri().startsWith("gateway://") ? "file_id" : "url",
                                            media.uri().startsWith("gateway://") ? media.uri().substring("gateway://".length()) : media.uri())
                                    .put("media_type", media.mimeType() == null ? "image/*" : media.mimeType());
                        }
                    }
                    item.set("content", content);
                } else {
                    item.put("content", text);
                }
            }
        }
    }

    private void writeGeminiMessages(ObjectNode root, List<CanonicalMessage> messages) {
        String systemPrompt = messages.stream()
                .filter(message -> message.role() == CanonicalMessageRole.SYSTEM)
                .map(this::joinText)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
        root.putObject("systemInstruction").put("text", systemPrompt);

        var array = root.putArray("contents");
        for (CanonicalMessage message : messages) {
            String role = normalizeRole(message.role());
            String text = joinText(message);
            List<CanonicalContentPart> mediaParts = mediaParts(message);
            boolean hasText = text != null && !text.isBlank();
            boolean hasMedia = !mediaParts.isEmpty();
            if ("system".equals(role) || (!hasText && !hasMedia && message.role() != CanonicalMessageRole.TOOL)) {
                continue;
            }
            String geminiRole = "assistant".equals(role) ? "model" : "user";
            var content = array.addObject().put("role", "tool".equals(role) ? "user" : geminiRole);
            var parts = content.putArray("parts");
            if ("tool".equals(role)) {
                CanonicalContentPart toolResult = toolResult(message);
                parts.addObject()
                        .putObject("functionResponse")
                        .put("name", toolResult == null || toolResult.toolName() == null ? "tool" : toolResult.toolName())
                        .putObject("response")
                        .put("content", toolResult == null || toolResult.text() == null ? "" : toolResult.text());
            } else {
                if (hasText) {
                    parts.addObject().put("text", text);
                }
                for (CanonicalContentPart media : mediaParts) {
                    parts.addObject()
                            .putObject("fileData")
                            .put("mimeType", media.mimeType() == null ? "application/octet-stream" : media.mimeType())
                            .put(media.uri().startsWith("gateway://") ? "fileId" : "fileUri",
                                    media.uri().startsWith("gateway://") ? media.uri().substring("gateway://".length()) : media.uri());
                }
            }
        }
    }

    private String lastUserMessage(List<CanonicalMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == CanonicalMessageRole.USER)
                .map(this::joinText)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private List<CanonicalContentPart> mediaParts(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.IMAGE || part.type() == CanonicalPartType.FILE)
                .toList();
    }

    private CanonicalContentPart toolResult(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TOOL_RESULT)
                .findFirst()
                .orElse(null);
    }

    private String joinText(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TEXT)
                .map(CanonicalContentPart::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String normalizeRole(CanonicalMessageRole role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    private UpstreamCredentialEntity getRequiredCredential(Long credentialId) {
        Optional<UpstreamCredentialEntity> credential = upstreamCredentialRepository.findById(credentialId);
        if (credential.isEmpty() || credential.get().isDeleted()) {
            throw new IllegalArgumentException("未找到对应的上游凭证。");
        }
        return credential.get();
    }
}
