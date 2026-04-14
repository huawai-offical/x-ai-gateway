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
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntime;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeContext;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalChatMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResource;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponseMapper;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamResponse;
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
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.provider.adapter.PreparedChatExecution;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiChatModelFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import java.net.URI;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

@Service
@Transactional
public class GatewayChatExecutionService {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final UpstreamCredentialRepository upstreamCredentialRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayObservabilityService gatewayObservabilityService;
    private final GatewayRequestLifecycleService gatewayRequestLifecycleService;
    private final DistributedKeyGovernanceService distributedKeyGovernanceService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final AccountSelectionService accountSelectionService;
    private final CredentialMaterialResolver credentialMaterialResolver;
    private final GatewayFileService gatewayFileService;
    private final GatewayRequestFeatureService gatewayRequestFeatureService;
    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;
    private final CanonicalChatMapper canonicalChatMapper;
    private final OpenAiChatModelFactory openAiChatModelFactory;
    private final AnthropicChatModelFactory anthropicChatModelFactory;
    private final GeminiChatModelFactory geminiChatModelFactory;
    private final List<GatewayChatRuntime> gatewayChatRuntimes;
    private final GatewayResponseMapper gatewayResponseMapper;
    private final GatewayProperties gatewayProperties;

    @Autowired
    public GatewayChatExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ProviderExecutionSupportService providerExecutionSupportService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayFileService gatewayFileService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            CanonicalChatMapper canonicalChatMapper,
            OpenAiChatModelFactory openAiChatModelFactory,
            AnthropicChatModelFactory anthropicChatModelFactory,
            GeminiChatModelFactory geminiChatModelFactory,
            List<GatewayChatRuntime> gatewayChatRuntimes,
            GatewayResponseMapper gatewayResponseMapper,
            GatewayProperties gatewayProperties) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.upstreamCredentialRepository = upstreamCredentialRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.gatewayObservabilityService = gatewayObservabilityService;
        this.gatewayRequestLifecycleService = gatewayRequestLifecycleService;
        this.distributedKeyGovernanceService = distributedKeyGovernanceService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.accountSelectionService = accountSelectionService;
        this.credentialMaterialResolver = credentialMaterialResolver;
        this.gatewayFileService = gatewayFileService;
        this.gatewayRequestFeatureService = gatewayRequestFeatureService;
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
        this.canonicalChatMapper = canonicalChatMapper;
        this.openAiChatModelFactory = openAiChatModelFactory;
        this.anthropicChatModelFactory = anthropicChatModelFactory;
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.gatewayChatRuntimes = gatewayChatRuntimes;
        this.gatewayResponseMapper = gatewayResponseMapper;
        this.gatewayProperties = gatewayProperties;
    }

    public GatewayChatExecutionService(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ProviderExecutionSupportService providerExecutionSupportService,
            UpstreamCredentialRepository upstreamCredentialRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayObservabilityService gatewayObservabilityService,
            GatewayRequestLifecycleService gatewayRequestLifecycleService,
            DistributedKeyGovernanceService distributedKeyGovernanceService,
            DistributedKeyQueryService distributedKeyQueryService,
            AccountSelectionService accountSelectionService,
            CredentialMaterialResolver credentialMaterialResolver,
            GatewayFileService gatewayFileService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler,
            OpenAiChatModelFactory openAiChatModelFactory,
            AnthropicChatModelFactory anthropicChatModelFactory,
            GeminiChatModelFactory geminiChatModelFactory,
            List<GatewayChatRuntime> gatewayChatRuntimes,
            GatewayResponseMapper gatewayResponseMapper,
            GatewayProperties gatewayProperties) {
        this(
                gatewayRouteSelectionService,
                providerExecutionSupportService,
                upstreamCredentialRepository,
                credentialCryptoService,
                gatewayObservabilityService,
                gatewayRequestLifecycleService,
                distributedKeyGovernanceService,
                distributedKeyQueryService,
                accountSelectionService,
                credentialMaterialResolver,
                gatewayFileService,
                gatewayRequestFeatureService,
                translationExecutionPlanCompiler,
                new CanonicalChatMapper(new tools.jackson.databind.ObjectMapper()),
                openAiChatModelFactory,
                anthropicChatModelFactory,
                geminiChatModelFactory,
                gatewayChatRuntimes,
                gatewayResponseMapper,
                gatewayProperties
        );
    }

    public AdminChatExecuteResponse execute(AdminChatExecuteRequest request) {
        ChatExecutionResponse response = execute(new ChatExecutionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                buildAdminMessages(request.systemPrompt(), request.userPrompt()),
                List.of(),
                null,
                request.temperature(),
                request.maxTokens()
        ));
        return new AdminChatExecuteResponse(
                response.requestId(),
                response.routeSelection(),
                response.text(),
                response.usage(),
                response.toolCalls()
        );
    }

    public ChatExecutionResponse execute(ChatExecutionRequest request) {
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        JsonNode routeBody = buildRouteBody(request);
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
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
                TranslationExecutionPlan executionPlan = translationExecutionPlanCompiler.compileSelected(
                        candidateSelection,
                        request.requestPath(),
                        semantics,
                        routeBody
                );
                UpstreamCredentialEntity credential = getRequiredCredential(candidate.candidate().credentialId());
                ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(candidateSelection, credential);
                try {
                    GatewayChatRuntime runtime = resolveRuntime(candidate.candidate());
                    GatewayChatRuntimeResult result = runtime.execute(new GatewayChatRuntimeContext(
                            candidateSelection,
                            credential,
                            credentialMaterial,
                            request,
                            executionPlan
                    ));
                    if (isEmptyChatResult(result)) {
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

                    GatewayUsageView usageView = gatewayResponseMapper.toUsageView(
                            result.usage(),
                            GatewayUsageCompleteness.FINAL,
                            GatewayUsageSource.DIRECT_RESPONSE
                    );
                    if (result.usage() != null && !result.usage().isEmpty()) {
                        gatewayObservabilityService.recordCacheUsage(
                                requestId,
                                finalSelection,
                                result.usage(),
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
                    return new ChatExecutionResponse(
                            requestId,
                            finalSelection,
                            result.text(),
                            result.usage(),
                            result.toolCalls(),
                            result.finishReason(),
                            result.reasoning()
                    );
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

    public ChatExecutionStreamResponse executeStream(ChatExecutionRequest request) {
        String requestId = gatewayObservabilityService.nextRequestId();
        Instant startedAt = Instant.now();
        JsonNode routeBody = buildRouteBody(request);
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                routeBody,
                GatewayClientFamily.GENERIC_OPENAI,
                true
        ));
        GatewayRequestSemantics semantics = gatewayRequestFeatureService.describe(request.requestPath(), routeBody);
        gatewayRequestLifecycleService.startRequest(requestId, selectionResult, request, true, startedAt);
        AtomicReference<GatewayUsage> lastVisibleUsage = new AtomicReference<>(GatewayUsage.empty());
        AtomicBoolean terminalRecorded = new AtomicBoolean(false);
        AtomicReference<RouteSelectionResult> finalSelectionRef = new AtomicReference<>(selectionResult);
        List<RouteExecutionAttempt> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        int maxAttempts = Math.min(selectionResult.candidates().size(), gatewayProperties.getRouting().getMaxFallbackAttempts());
        Flux<ChatExecutionStreamChunk> chunks = streamAttempt(
                        requestId,
                        selectionResult,
                        request,
                        routeBody,
                        semantics,
                        0,
                        maxAttempts,
                        attempts,
                        finalSelectionRef
                )
                .doOnNext(chunk -> {
                    if (chunk.usage() != null && !chunk.usage().isEmpty()) {
                        lastVisibleUsage.set(chunk.usage());
                    }
                    if (chunk.terminal()) {
                        GatewayUsageView usageView = terminalUsageView(chunk.usage(), lastVisibleUsage.get());
                        RouteSelectionResult finalSelection = finalSelectionRef.get().withAttempts(List.copyOf(attempts));
                        gatewayObservabilityService.recordRouteDecision(requestId, finalSelection);
                        recordTerminalUsage(requestId, finalSelection, request, startedAt, usageView, chunk.usage(), lastVisibleUsage.get());
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

        return new ChatExecutionStreamResponse(requestId, selectionResult, chunks);
    }

    public GatewayResponse executeGatewayResponse(ChatExecutionRequest request) {
        return gatewayResponseMapper.toGatewayResponse(execute(request));
    }

    public GatewayStreamResponse executeGatewayStream(ChatExecutionRequest request) {
        return gatewayResponseMapper.toGatewayStreamResponse(executeStream(request));
    }

    private Flux<ChatExecutionStreamChunk> streamAttempt(
            String requestId,
            RouteSelectionResult baseSelection,
            ChatExecutionRequest request,
            JsonNode routeBody,
            GatewayRequestSemantics semantics,
            int candidateIndex,
            int maxAttempts,
            List<RouteExecutionAttempt> attempts,
            AtomicReference<RouteSelectionResult> finalSelectionRef) {
        RouteCandidateView candidate = baseSelection.candidates().get(candidateIndex);
        RouteSelectionResult candidateSelection = selectionForCandidate(baseSelection, candidate, attempts);
        finalSelectionRef.set(candidateSelection);

        UpstreamCredentialEntity credential = getRequiredCredential(candidate.candidate().credentialId());
        ResolvedCredentialMaterial credentialMaterial = credentialMaterialResolver.resolve(candidateSelection, credential);
        TranslationExecutionPlan executionPlan = translationExecutionPlanCompiler.compileSelected(
                candidateSelection,
                request.requestPath(),
                semantics,
                routeBody
        );
        GatewayChatRuntime runtime = resolveRuntime(candidate.candidate());
        AtomicBoolean firstOutputCommitted = new AtomicBoolean(false);
        AtomicBoolean successRecorded = new AtomicBoolean(false);

        return runtime.executeStream(new GatewayChatRuntimeContext(
                        candidateSelection,
                        credential,
                        credentialMaterial,
                        request,
                        executionPlan
                ))
                .doOnNext(chunk -> {
                    if (isVisibleStreamChunk(chunk)) {
                        firstOutputCommitted.set(true);
                    }
                    if (chunk.terminal() && successRecorded.compareAndSet(false, true)) {
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
                                finalSelectionRef
                        );
                    }
                    return Flux.error(error);
                });
    }

    private void recordTerminalUsage(
            String requestId,
            RouteSelectionResult selectionResult,
            ChatExecutionRequest request,
            Instant startedAt,
            GatewayUsageView usageView,
            GatewayUsage terminalUsage,
            GatewayUsage lastVisibleUsage) {
        GatewayUsage usageForLog = terminalUsage != null && !terminalUsage.isEmpty() ? terminalUsage : lastVisibleUsage;
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

    private GatewayUsageView terminalUsageView(GatewayUsage terminalUsage, GatewayUsage lastVisibleUsage) {
        if (terminalUsage != null && !terminalUsage.isEmpty()) {
            return gatewayResponseMapper.toUsageView(
                    terminalUsage,
                    GatewayUsageCompleteness.FINAL,
                    GatewayUsageSource.PROVIDER_FINAL
            );
        }
        if (lastVisibleUsage != null && !lastVisibleUsage.isEmpty()) {
            return gatewayResponseMapper.toUsageView(
                    lastVisibleUsage,
                    GatewayUsageCompleteness.PARTIAL,
                    GatewayUsageSource.LAST_VISIBLE
            );
        }
        return GatewayUsageView.empty();
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

    private boolean isEmptyChatResult(GatewayChatRuntimeResult result) {
        if (result == null) {
            return true;
        }
        boolean hasText = result.text() != null && !result.text().isBlank();
        boolean hasReasoning = result.reasoning() != null && !result.reasoning().isBlank();
        boolean hasToolCalls = result.toolCalls() != null && !result.toolCalls().isEmpty();
        return !hasText && !hasReasoning && !hasToolCalls;
    }

    private boolean isVisibleStreamChunk(ChatExecutionStreamChunk chunk) {
        if (chunk == null) {
            return false;
        }
        return (chunk.textDelta() != null && !chunk.textDelta().isBlank())
                || (chunk.reasoningDelta() != null && !chunk.reasoningDelta().isBlank())
                || (chunk.toolCalls() != null && !chunk.toolCalls().isEmpty())
                || chunk.terminal();
    }

    private GatewayChatRuntime resolveRuntime(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
        return gatewayChatRuntimes.stream()
                .filter(runtime -> runtime.supports(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到匹配的聊天运行时：" + candidate.providerType()));
    }

    private ChatResponse executeOpenAi(RouteSelectionResult selectionResult, String baseUrl, String apiKey, ChatExecutionRequest request) {
        OpenAiChatOptions baseOptions = OpenAiChatOptions.builder()
                .model(selectionResult.resolvedModelKey())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();
        PreparedChatExecution<OpenAiChatOptions> prepared = providerExecutionSupportService.prepareOpenAi(
                selectionResult,
                baseOptions,
                request.tools(),
                request.toolChoice()
        );
        OpenAiChatModel model = openAiChatModelFactory.create(baseUrl, apiKey, prepared.options());
        return call(model, buildPrompt(prepared.options(), request));
    }

    private ChatResponse executeAnthropic(RouteSelectionResult selectionResult, String baseUrl, String apiKey, ChatExecutionRequest request) {
        AnthropicChatOptions baseOptions = AnthropicChatOptions.builder()
                .model(selectionResult.resolvedModelKey())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();
        PreparedChatExecution<AnthropicChatOptions> prepared = providerExecutionSupportService.prepareAnthropic(
                selectionResult,
                baseOptions,
                request.tools(),
                request.toolChoice()
        );
        AnthropicChatModel model = anthropicChatModelFactory.create(baseUrl, apiKey, prepared.options());
        return call(model, buildPrompt(prepared.options(), request));
    }

    private ChatResponse executeGemini(RouteSelectionResult selectionResult, String baseUrl, String apiKey, ChatExecutionRequest request) {
        GoogleGenAiChatOptions baseOptions = GoogleGenAiChatOptions.builder()
                .model(selectionResult.resolvedModelKey())
                .temperature(request.temperature())
                .maxOutputTokens(request.maxTokens())
                .build();
        PreparedChatExecution<GoogleGenAiChatOptions> prepared = providerExecutionSupportService.prepareGemini(
                selectionResult,
                baseOptions,
                request.tools()
        );
        GoogleGenAiChatModel model = geminiChatModelFactory.create(baseUrl, apiKey, prepared.options());
        return call(model, buildPrompt(prepared.options(), request));
    }

    private ChatResponse call(ChatModel model, Prompt prompt) {
        try {
            return model.call(prompt);
        } finally {
            closeModel(model, SignalType.ON_COMPLETE);
        }
    }

    private void closeModel(ChatModel model, SignalType signalType) {
        if (signalType == null) {
            return;
        }

        if (model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                return;
            } catch (Exception ignored) {
                // ignore
            }
        }

        if (model instanceof DisposableBean disposableBean) {
            try {
                disposableBean.destroy();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private Prompt buildPrompt(Object options, ChatExecutionRequest request) {
        List<Message> messages = request.messages().stream()
                .filter(this::isUsableMessage)
                .map(message -> toPromptMessage(request.distributedKeyPrefix(), message))
                .toList();
        return new Prompt(messages, (org.springframework.ai.chat.prompt.ChatOptions) options);
    }

    private JsonNode buildRouteBody(ChatExecutionRequest request) {
        CanonicalRequest canonicalRequest = canonicalChatMapper.toCanonicalRequest(request);
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

    private List<ChatExecutionRequest.MessageInput> buildAdminMessages(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return List.of(new ChatExecutionRequest.MessageInput("user", userPrompt, null, null, List.of()));
        }
        return List.of(
                new ChatExecutionRequest.MessageInput("system", systemPrompt, null, null, List.of()),
                new ChatExecutionRequest.MessageInput("user", userPrompt, null, null, List.of())
        );
    }

    private Message toPromptMessage(String distributedKeyPrefix, ChatExecutionRequest.MessageInput message) {
        return switch (message.role().trim().toLowerCase()) {
            case "system" -> new SystemMessage(message.content() == null ? "" : message.content().trim());
            case "assistant", "model" -> new org.springframework.ai.chat.messages.AssistantMessage(message.content() == null ? "" : message.content().trim());
            case "tool" -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            message.toolCallId() == null ? "tool-call" : message.toolCallId(),
                            message.toolName() == null ? "tool" : message.toolName(),
                            message.content() == null ? "" : message.content().trim()
                    )))
                    .build();
            default -> {
                if (message.media() != null && !message.media().isEmpty()) {
                    List<Media> media = message.media().stream()
                            .filter(item -> item.url() != null && !item.url().isBlank())
                            .map(item -> toMedia(distributedKeyPrefix, item))
                            .toList();
                    yield UserMessage.builder()
                            .text(message.content() == null ? "" : message.content().trim())
                            .media(media)
                            .build();
                }
                yield new UserMessage(message.content() == null ? "" : message.content().trim());
            }
        };
    }

    private boolean isUsableMessage(ChatExecutionRequest.MessageInput message) {
        boolean hasText = message.content() != null && !message.content().isBlank();
        boolean hasMedia = message.media() != null && !message.media().isEmpty();
        return hasText || hasMedia;
    }

    private Media toMedia(String distributedKeyPrefix, ChatExecutionRequest.MediaInput item) {
        if (item.url() != null && item.url().startsWith("gateway://")) {
            String fileKey = item.url().substring("gateway://".length());
            Long distributedKeyId = distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                    .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                    .id();
            GatewayFileResource resource = gatewayFileService.resolveFileResource(fileKey, distributedKeyId);
            return Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(resource.mimeType()))
                    .data(resource.resource())
                    .name(resource.filename())
                    .build();
        }

        return Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(item.mimeType() == null || item.mimeType().isBlank()
                        ? ("file".equalsIgnoreCase(item.kind()) ? "application/octet-stream" : "image/*")
                        : item.mimeType()))
                .data(URI.create(item.url()))
                .name(item.name())
                .build();
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
