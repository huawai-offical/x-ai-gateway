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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
            GatewayProperties gatewayProperties) {
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
    }

    public AdminChatExecuteResponse execute(AdminChatExecuteRequest request) {
        CanonicalExecutionResult response = executeGatewayResponse(buildAdminRequest(request));
        return new AdminChatExecuteResponse(
                response.requestId(),
                response.routeSelection(),
                response.plan().executionBackend(),
                response.response().outputText(),
                toGatewayUsage(response.response().usage()),
                toGatewayToolCalls(response.response().toolCalls())
        );
    }

    public CanonicalExecutionResult executeGatewayResponse(CanonicalRequest request) {
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        JsonNode routeBody = buildRouteBody(request);
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.ingressProtocol().name().toLowerCase(),
                request.requestPath(),
                request.requestedModel(),
                routeBody,
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.requestPath(), routeBody);
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, false, startedAt);

        try {
            List<RouteExecutionAttempt> attempts = new ArrayList<>();
            int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
            RuntimeException lastException = null;

            for (int index = 0; index < maxAttempts; index++) {
                RouteCandidateView candidate = selectionResult.candidates().get(index);
                RouteSelectionResult candidateSelection = selectionForCandidate(selectionResult, candidate, attempts);
                CanonicalExecutionPlanCompilation executionPlanCompilation = translationExecutionPlanCompiler.compileSelected(
                        candidateSelection,
                        request,
                        semantics,
                        routeBody
                );
                UpstreamCredentialEntity credential = getRequiredCredential(candidate.candidate().credentialId());
                ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(candidateSelection, credential);
                try {
                    GatewayChatRuntime runtime = resolveRuntime(candidate.candidate(), executionPlanCompilation.canonicalPlan().executionBackend());
                    CanonicalResponse result = runtime.execute(new GatewayChatRuntimeContext(
                            candidateSelection,
                            credential,
                            credentialMaterial,
                            request,
                            executionPlanCompilation.canonicalPlan()
                    ));
                    if (isEmptyCanonicalResult(result)) {
                        throw new IllegalStateException("上游响应为空。");
                    }

                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidate.candidate().credentialId(),
                            candidate.candidate().providerType().name(),
                            "SUCCEEDED",
                            candidateSelection.selectionSource().name()
                    ));
                    RouteSelectionResult finalSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                    gatewayRouteSelectionService.recordSuccessfulSelection(finalSelection);
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
                            request,
                            false,
                            usageView,
                            startedAt
                    );
                    return new CanonicalExecutionResult(requestId, finalSelection, executionPlanCompilation.canonicalPlan(), enriched);
                } catch (RuntimeException exception) {
                    attempts.add(new RouteExecutionAttempt(
                            index + 1,
                            candidate.candidate().credentialId(),
                            candidate.candidate().providerType().name(),
                            "FAILED_BEFORE_FIRST_BYTE",
                            fallbackDetail(exception)
                    ));
                    gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                    gatewayRouteSelectionService.markCredentialCooldown(candidate.candidate().credentialId(), fallbackDetail(exception));
                    lastException = exception;
                    if (!shouldFallback(exception) || index == maxAttempts - 1) {
                        RouteSelectionResult failedSelection = candidateSelection.withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(requestId, failedSelection);
                        gatewayRequestLifecycleService.failRequest(
                                requestId,
                                failedSelection,
                                request,
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
            throw new IllegalStateException("当前 provider 候选没有可用执行尝试。");
        } catch (RuntimeException exception) {
            throw exception;
        } finally {
            distributedKeyGovernanceService.releaseConcurrency(selectionResult.governanceReservationKey());
        }
    }

    public CanonicalExecutionStreamResult executeGatewayStream(CanonicalRequest request) {
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        JsonNode routeBody = buildRouteBody(request);
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.ingressProtocol().name().toLowerCase(),
                request.requestPath(),
                request.requestedModel(),
                routeBody,
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.requestPath(), routeBody);
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, true, startedAt);
        AtomicReference<CanonicalUsage> lastVisibleUsage = new AtomicReference<>(CanonicalUsage.empty());
        AtomicBoolean terminalRecorded = new AtomicBoolean(false);
        AtomicReference<RouteSelectionResult> finalSelectionRef = new AtomicReference<>(selectionResult);
        AtomicReference<CanonicalExecutionPlan> planRef = new AtomicReference<>();
        List<RouteExecutionAttempt> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
        Flux<CanonicalStreamEvent> chunks = streamAttempt(
                        requestId,
                        selectionResult,
                        request,
                        routeBody,
                        semantics,
                        0,
                        maxAttempts,
                        attempts,
                        finalSelectionRef,
                        planRef
                )
                .doOnNext(event -> {
                    if (event.usage() != null && event.usage().present()) {
                        lastVisibleUsage.set(event.usage());
                    }
                    if (event.terminal()) {
                        GatewayUsageView usageView = terminalUsageView(event.usage(), lastVisibleUsage.get());
                        RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                        recordTerminalUsage(requestId, finalSelection, request, startedAt, usageView, event.usage(), lastVisibleUsage.get());
                        terminalRecorded.set(true);
                    }
                })
                .doOnComplete(() -> {
                    if (!terminalRecorded.get()) {
                        GatewayUsageView usageView = terminalUsageView(null, lastVisibleUsage.get());
                        RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                        recordTerminalUsage(requestId, finalSelection, request, startedAt, usageView, null, lastVisibleUsage.get());
                    }
                })
                .doOnError(error -> {
                    RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                    gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                    gatewayRouteSelectionService.invalidateSelection(finalSelection);
                    gatewayRequestLifecycleService.failRequest(
                            requestId,
                            finalSelection,
                            request,
                            true,
                            error,
                            terminalUsageView(null, lastVisibleUsage.get()),
                            startedAt
                    );
                })
                .doOnCancel(() -> {
                    RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                    gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                    gatewayRouteSelectionService.invalidateSelection(finalSelection);
                    gatewayRequestLifecycleService.cancelRequest(
                            requestId,
                            finalSelection,
                            request,
                            true,
                            terminalUsageView(null, lastVisibleUsage.get()),
                            startedAt
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
            AtomicReference<CanonicalExecutionPlan> planRef) {
        RouteCandidateView candidate = baseSelection.candidates().get(candidateIndex);
        RouteSelectionResult candidateSelection = selectionForCandidate(baseSelection, candidate, attempts);
        finalSelectionRef.set(candidateSelection);

        UpstreamCredentialEntity credential = getRequiredCredential(candidate.candidate().credentialId());
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(candidateSelection, credential);
        CanonicalExecutionPlanCompilation executionPlanCompilation = translationExecutionPlanCompiler.compileSelected(
                candidateSelection,
                request,
                semantics,
                routeBody
        );
        planRef.set(executionPlanCompilation.canonicalPlan());
        GatewayChatRuntime runtime = resolveRuntime(candidate.candidate(), executionPlanCompilation.canonicalPlan().executionBackend());
        AtomicBoolean firstOutputCommitted = new AtomicBoolean(false);
        AtomicBoolean successRecorded = new AtomicBoolean(false);

        return runtime.executeStream(new GatewayChatRuntimeContext(
                        candidateSelection,
                        credential,
                        credentialMaterial,
                        request,
                        executionPlanCompilation.canonicalPlan()
                ))
                .doOnNext(event -> {
                    if (isVisibleStreamEvent(event)) {
                        firstOutputCommitted.set(true);
                    }
                    if (event.terminal() && successRecorded.compareAndSet(false, true)) {
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
                    }
                })
                .onErrorResume(error -> {
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
                    gatewayRouteSelectionService.invalidateSelection(candidateSelection);
                    gatewayRouteSelectionService.markCredentialCooldown(candidate.candidate().credentialId(), fallbackDetail(error));
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
                                planRef
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
            CanonicalUsage lastVisibleUsage) {
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
                startedAt
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
                result.finishReason()
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

    private boolean isVisibleStreamEvent(CanonicalStreamEvent event) {
        if (event == null) {
            return false;
        }
        return (event.textDelta() != null && !event.textDelta().isBlank())
                || (event.reasoningDelta() != null && !event.reasoningDelta().isBlank())
                || (event.toolCalls() != null && !event.toolCalls().isEmpty())
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

        return root;
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
