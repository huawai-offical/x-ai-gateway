package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.credential.CredentialMaterialResolver;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatPromptBuilder;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntime;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayChatRuntimeContext;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResource;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesRequestMapper;
import com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentRequestMapper;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionRequestMapper;
import com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesRequestMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayChatExecutionServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldResolveGatewayFileReferenceToSpringAiMedia() {
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        GatewayFileService gatewayFileService = Mockito.mock(GatewayFileService.class);
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
                CanonicalContentPart.file("application/pdf", "gateway://file-123", "doc.pdf")
        );

        assertEquals("application/pdf", ReflectionTestUtils.invokeMethod(media, "getMimeType").toString());
    }

    @Test
    void shouldFallbackToSecondChatCandidateBeforeFirstByte() {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        GatewayRequestLifecycleService gatewayRequestLifecycleService = Mockito.mock(GatewayRequestLifecycleService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayProperties gatewayProperties = new GatewayProperties();

        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend backend() {
                return com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend.NATIVE;
            }

            @Override
            public boolean supports(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public CanonicalResponse execute(GatewayChatRuntimeContext context) {
                if (context.selectionResult().selectedCandidate().candidate().credentialId().equals(101L)) {
                    throw new IllegalStateException("upstream 503");
                }
                return new CanonicalResponse(
                        null,
                        context.selectionResult().publicModel(),
                        "fallback ok",
                        null,
                        List.of(),
                        CanonicalUsage.empty(),
                        com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.STOP
                );
            }

            @Override
            public reactor.core.publisher.Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
                return reactor.core.publisher.Flux.empty();
            }
        };

        GatewayChatExecutionService service = service(
                routeSelectionService,
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
                List.of(runtime),
                gatewayProperties
        );

        CanonicalRequest request = canonicalRequest();

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-chat-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.any(CanonicalRequest.class), Mockito.any(), Mockito.any()))
                .thenReturn(canonicalCompilation("openai", "/v1/chat/completions", "gpt-4o"));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
        Mockito.when(upstreamCredentialRepository.findById(202L)).thenReturn(Optional.of(credential(202L)));
        Mockito.when(credentialMaterialResolver.resolve(Mockito.any(), Mockito.any())).thenAnswer(invocation ->
                new com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial(
                        ((UpstreamCredentialEntity) invocation.getArgument(1)).getId(),
                        null,
                        CredentialAuthKind.API_KEY,
                        "api-key",
                        null,
                        java.util.Map.of(),
                        null,
                        "test"
                )
        );

        var response = service.executeGatewayResponse(request);

        assertEquals("fallback ok", response.response().outputText());
        assertEquals(202L, response.routeSelection().selectedCandidate().candidate().credentialId());
        assertTrue(response.routeSelection().attempts().stream().anyMatch(item -> "FAILED_BEFORE_FIRST_BYTE".equals(item.outcome())));
        Mockito.verify(routeSelectionService).markCredentialCooldown(101L, "upstream 503");
    }

    @Test
    void shouldFallbackToSecondStreamCandidateBeforeFirstChunk() {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        GatewayRequestLifecycleService gatewayRequestLifecycleService = Mockito.mock(GatewayRequestLifecycleService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        GatewayProperties gatewayProperties = new GatewayProperties();

        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend backend() {
                return com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend.NATIVE;
            }

            @Override
            public boolean supports(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public CanonicalResponse execute(GatewayChatRuntimeContext context) {
                return null;
            }

            @Override
            public reactor.core.publisher.Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
                if (context.selectionResult().selectedCandidate().candidate().credentialId().equals(101L)) {
                    return reactor.core.publisher.Flux.error(new IllegalStateException("stream upstream 503"));
                }
                return reactor.core.publisher.Flux.just(
                        new CanonicalStreamEvent(CanonicalStreamEventType.TEXT_DELTA, "hello", null, List.of(), CanonicalUsage.empty(), false, null, null, null),
                        new CanonicalStreamEvent(CanonicalStreamEventType.COMPLETED, null, null, List.of(), CanonicalUsage.empty(), true, com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.STOP, "hello", null)
                );
            }
        };

        GatewayChatExecutionService service = service(
                routeSelectionService,
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
                List.of(runtime),
                gatewayProperties
        );

        CanonicalRequest request = canonicalRequest();

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-chat-stream-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.any(CanonicalRequest.class), Mockito.any(), Mockito.any()))
                .thenReturn(canonicalCompilation("openai", "/v1/chat/completions", "gpt-4o"));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
        Mockito.when(upstreamCredentialRepository.findById(202L)).thenReturn(Optional.of(credential(202L)));
        Mockito.when(credentialMaterialResolver.resolve(Mockito.any(), Mockito.any())).thenAnswer(invocation ->
                new com.prodigalgal.xaigateway.gateway.core.credential.ResolvedCredentialMaterial(
                        ((UpstreamCredentialEntity) invocation.getArgument(1)).getId(),
                        null,
                        CredentialAuthKind.API_KEY,
                        "api-key",
                        null,
                        java.util.Map.of(),
                        null,
                        "test"
                )
        );

        var response = service.executeGatewayStream(request);
        var chunks = response.events().collectList().block();

        assertEquals(2, chunks.size());
        assertEquals("hello", chunks.get(0).textDelta());
        Mockito.verify(routeSelectionService).markCredentialCooldown(101L, "stream upstream 503");
    }

    private GatewayChatExecutionService service(
            GatewayRouteSelectionService routeSelectionService,
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
            List<GatewayChatRuntime> gatewayChatRuntimes,
            GatewayProperties gatewayProperties) {
        return new GatewayChatExecutionService(
                routeSelectionService,
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
                new OpenAiChatCompletionRequestMapper(objectMapper),
                new OpenAiResponsesRequestMapper(objectMapper),
                new AnthropicMessagesRequestMapper(objectMapper),
                new GeminiGenerateContentRequestMapper(objectMapper),
                gatewayChatRuntimes,
                gatewayProperties
        );
    }

    private CanonicalRequest canonicalRequest() {
        return new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private RouteSelectionResult selectionResultWithFallbackCandidates() {
        com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView first = new com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView(
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
        com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView second = new com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView(
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
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setProviderType(ProviderType.OPENAI_DIRECT);
        entity.setBaseUrl("https://api.openai.com");
        entity.setApiKeyCiphertext("cipher");
        return entity;
    }

    private CanonicalExecutionPlanCompilation canonicalCompilation(String protocol, String requestPath, String model) {
        return new CanonicalExecutionPlanCompilation(
                new CanonicalExecutionPlan(
                        true,
                        CanonicalIngressProtocol.from(protocol),
                        requestPath,
                        model,
                        model,
                        model,
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        ExecutionKind.NATIVE,
                        ExecutionBackend.NATIVE,
                        List.of(ExecutionBackend.NATIVE),
                        "test",
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        List.of(InteropFeature.CHAT_TEXT),
                        java.util.Map.of("chat_text", InteropCapabilityLevel.NATIVE),
                        List.of(),
                        List.of()
                ),
                selectionResultWithFallbackCandidates(),
                new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ),
                canonicalRequest()
        );
    }
}
