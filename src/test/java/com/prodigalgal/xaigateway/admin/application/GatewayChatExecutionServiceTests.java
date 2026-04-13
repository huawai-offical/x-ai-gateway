package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntime;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeContext;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatPromptBuilder;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResource;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponseMapper;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteExecutionAttempt;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiChatModelFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayChatExecutionServiceTests {

    @Test
    void shouldResolveGatewayFileReferenceToSpringAiMedia() {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        ProviderExecutionSupportService providerExecutionSupportService = Mockito.mock(ProviderExecutionSupportService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        OpenAiChatModelFactory openAiChatModelFactory = Mockito.mock(OpenAiChatModelFactory.class);
        AnthropicChatModelFactory anthropicChatModelFactory = Mockito.mock(AnthropicChatModelFactory.class);
        GeminiChatModelFactory geminiChatModelFactory = Mockito.mock(GeminiChatModelFactory.class);
        GatewayChatPromptBuilder promptBuilder = new GatewayChatPromptBuilder(distributedKeyQueryService, gatewayFileService);

        Mockito.when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test"))
                .thenReturn(Optional.of(new DistributedKeyView(
                        1L,
                        "test",
                        "sk-gw-test",
                        "masked",
                        List.of(),
                        List.of(),
                        List.of()
                )));
        Mockito.when(gatewayFileService.resolveFileResource("file-123", 1L))
                .thenReturn(new GatewayFileResource(
                        "file-123",
                        "application/pdf",
                        "doc.pdf",
                        new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8))
                ));

        Object media = ReflectionTestUtils.invokeMethod(
                promptBuilder,
                "toMedia",
                "sk-gw-test",
                new ChatExecutionRequest.MediaInput(
                        "file",
                        "application/pdf",
                        "gateway://file-123",
                        "doc.pdf"
                )
        );

        assertEquals("application/pdf", ReflectionTestUtils.invokeMethod(media, "getMimeType").toString());
    }

    @Test
    void shouldFallbackToSecondChatCandidateBeforeFirstByte() {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        ProviderExecutionSupportService providerExecutionSupportService = Mockito.mock(ProviderExecutionSupportService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        GatewayRequestLifecycleService gatewayRequestLifecycleService = Mockito.mock(GatewayRequestLifecycleService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayResponseMapper gatewayResponseMapper = new GatewayResponseMapper();
        GatewayProperties gatewayProperties = new GatewayProperties();
        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public boolean supports(CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context) {
                if (context.selectionResult().selectedCandidate().candidate().credentialId().equals(101L)) {
                    throw new IllegalStateException("upstream 503");
                }
                return new GatewayChatRuntimeResult("fallback ok", GatewayUsage.empty(), List.of(), "stop");
            }

            @Override
            public reactor.core.publisher.Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context) {
                return reactor.core.publisher.Flux.empty();
            }
        };

        GatewayChatExecutionService service = new GatewayChatExecutionService(
                routeSelectionService,
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
                Mockito.mock(OpenAiChatModelFactory.class),
                Mockito.mock(AnthropicChatModelFactory.class),
                Mockito.mock(GeminiChatModelFactory.class),
                List.of(runtime),
                gatewayResponseMapper,
                gatewayProperties
        );

        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                List.of(new ChatExecutionRequest.MessageInput("user", "hello", null, null, List.of())),
                List.of(),
                null,
                null,
                null
        );

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-chat-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mockito.mock(TranslationExecutionPlan.class));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.CHAT,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.CHAT_COMPLETION,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
        Mockito.when(upstreamCredentialRepository.findById(202L)).thenReturn(Optional.of(credential(202L)));
        Mockito.when(credentialMaterialResolver.resolve(Mockito.any(), Mockito.any())).thenAnswer(invocation ->
                new com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial(
                        ((UpstreamCredentialEntity) invocation.getArgument(1)).getId(),
                        null,
                        com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind.API_KEY,
                        "api-key",
                        null,
                        java.util.Map.of(),
                        null,
                        "test"
                )
        );

        ChatExecutionResponse response = service.execute(request);

        assertEquals("fallback ok", response.text());
        assertEquals(202L, response.routeSelection().selectedCandidate().candidate().credentialId());
        assertTrue(response.routeSelection().attempts().stream().anyMatch(item -> "FAILED_BEFORE_FIRST_BYTE".equals(item.outcome())));
        Mockito.verify(routeSelectionService).markCredentialCooldown(101L, "upstream 503");
    }

    @Test
    void shouldFallbackToSecondStreamCandidateBeforeFirstChunk() {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        ProviderExecutionSupportService providerExecutionSupportService = Mockito.mock(ProviderExecutionSupportService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        GatewayRequestLifecycleService gatewayRequestLifecycleService = Mockito.mock(GatewayRequestLifecycleService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
        com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayResponseMapper gatewayResponseMapper = new GatewayResponseMapper();
        GatewayProperties gatewayProperties = new GatewayProperties();
        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public boolean supports(CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context) {
                return null;
            }

            @Override
            public reactor.core.publisher.Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context) {
                if (context.selectionResult().selectedCandidate().candidate().credentialId().equals(101L)) {
                    return reactor.core.publisher.Flux.error(new IllegalStateException("stream upstream 503"));
                }
                return reactor.core.publisher.Flux.just(
                        new ChatExecutionStreamChunk("hello", null, GatewayUsage.empty(), false),
                        new ChatExecutionStreamChunk(null, "stop", GatewayUsage.empty(), true)
                );
            }
        };

        GatewayChatExecutionService service = new GatewayChatExecutionService(
                routeSelectionService,
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
                Mockito.mock(OpenAiChatModelFactory.class),
                Mockito.mock(AnthropicChatModelFactory.class),
                Mockito.mock(GeminiChatModelFactory.class),
                List.of(runtime),
                gatewayResponseMapper,
                gatewayProperties
        );

        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                List.of(new ChatExecutionRequest.MessageInput("user", "hello", null, null, List.of())),
                List.of(),
                null,
                null,
                null
        );

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-chat-stream-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(Mockito.mock(TranslationExecutionPlan.class));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics(
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType.CHAT,
                        com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation.CHAT_COMPLETION,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
        Mockito.when(upstreamCredentialRepository.findById(202L)).thenReturn(Optional.of(credential(202L)));
        Mockito.when(credentialMaterialResolver.resolve(Mockito.any(), Mockito.any())).thenAnswer(invocation ->
                new com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial(
                        ((UpstreamCredentialEntity) invocation.getArgument(1)).getId(),
                        null,
                        com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind.API_KEY,
                        "api-key",
                        null,
                        java.util.Map.of(),
                        null,
                        "test"
                )
        );

        var response = service.executeStream(request);
        var chunks = response.chunks().collectList().block();

        assertEquals(2, chunks.size());
        assertEquals("hello", chunks.get(0).textDelta());
        Mockito.verify(routeSelectionService).markCredentialCooldown(101L, "stream upstream 503");
    }

    private RouteSelectionResult selectionResultWithFallbackCandidates() {
        CatalogCandidateView first = new CatalogCandidateView(
                101L,
                "candidate-a",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        CatalogCandidateView second = new CatalogCandidateView(
                202L,
                "candidate-b",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView firstView = new RouteCandidateView(first, 11L, 10, 100, "NATIVE", 3);
        RouteCandidateView secondView = new RouteCandidateView(second, 12L, 10, 90, "NATIVE", 3);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
                "openai",
                "prefix-hash",
                "fingerprint",
                "gpt-4o",
                com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily.GENERIC_OPENAI,
                List.of(),
                null,
                RouteSelectionSource.WEIGHTED_HASH,
                firstView,
                List.of(firstView, secondView),
                List.of(
                        new RouteCandidateEvaluation(firstView, true, "HEALTHY", null, false, RouteSelectionSource.WEIGHTED_HASH, 100d, List.of(), List.of()),
                        new RouteCandidateEvaluation(secondView, true, "HEALTHY", null, false, RouteSelectionSource.WEIGHTED_HASH, 90d, List.of(), List.of())
                ),
                List.of()
        );
    }

    private UpstreamCredentialEntity credential(Long id) {
        UpstreamCredentialEntity entity = new UpstreamCredentialEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", id);
        entity.setProviderType(ProviderType.OPENAI_DIRECT);
        entity.setBaseUrl("https://api.openai.com");
        entity.setApiKeyCiphertext("cipher");
        return entity;
    }
}
