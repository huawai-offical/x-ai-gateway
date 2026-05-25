package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterService;
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
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolution;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionReport;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixService;
import com.prodigalgal.xaigateway.gateway.core.interop.NonChatDegradationPolicyService;
import com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyService;
import com.prodigalgal.xaigateway.gateway.core.interop.NonChatTargetResolutionService;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestLifecycleService;
import com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailService;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceContentKind;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceDirection;
import com.prodigalgal.xaigateway.gateway.core.observability.RequestTraceStage;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.AuthStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ErrorSchemaStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.PathStrategy;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
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
    void shouldFailChatExecutionBeforeRuntimeWhenPlannerBlocksCapability() {
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
        GatewayChatRuntime runtime = Mockito.mock(GatewayChatRuntime.class);
        GatewayProperties gatewayProperties = new GatewayProperties();

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

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-blocked-plan-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.RESPONSE,
                        TranslationOperation.RESPONSE_CREATE,
                        List.of(InteropFeature.RESPONSE_OBJECT),
                        true
                ));
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.any(CanonicalRequest.class), Mockito.any(), Mockito.any()))
                .thenReturn(blockedCanonicalCompilation("native_hosted_tool_required"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.executeGatewayResponse(responsesRequest())
        );

        assertTrue(exception.getMessage().contains("native_hosted_tool_required"));
        Mockito.verify(upstreamCredentialRepository, Mockito.never()).findById(Mockito.anyLong());
        Mockito.verify(credentialMaterialResolver, Mockito.never()).resolve(Mockito.any(), Mockito.any());
        Mockito.verify(runtime, Mockito.never()).execute(Mockito.any());
        Mockito.verify(routeSelectionService, Mockito.never()).markCredentialCooldown(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void shouldRejectOpenAiProviderFileIdBeforeRuntimeWhenRouteTranslatesToAnthropic() throws Exception {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        GatewayRequestLifecycleService gatewayRequestLifecycleService = Mockito.mock(GatewayRequestLifecycleService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = new GatewayRequestFeatureService();
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = realCompiler(
                routeSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService
        );
        GatewayChatRuntime runtime = Mockito.mock(GatewayChatRuntime.class);
        GatewayProperties gatewayProperties = new GatewayProperties();

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

        var selection = selectionResultFor(anthropicCandidate());
        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-fileid-block-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selection);
        Mockito.when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any())).thenReturn(nativeCapabilityReport());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.executeGatewayResponse(openAiChatFileIdRequest())
        );

        assertTrue(exception.getMessage().contains("content.file.provider_file_id"));
        assertTrue(exception.getMessage().contains("native_route_required"));
        Mockito.verify(upstreamCredentialRepository, Mockito.never()).findById(Mockito.anyLong());
        Mockito.verify(credentialMaterialResolver, Mockito.never()).resolve(Mockito.any(), Mockito.any());
        Mockito.verify(runtime, Mockito.never()).execute(Mockito.any());
        Mockito.verify(routeSelectionService, Mockito.never()).markCredentialCooldown(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void shouldRejectGeminiProviderFileIdBeforeRuntimeWhenRouteTranslatesToOpenAi() throws Exception {
        GatewayRouteSelectionService routeSelectionService = Mockito.mock(GatewayRouteSelectionService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        CredentialCryptoService credentialCryptoService = Mockito.mock(CredentialCryptoService.class);
        GatewayObservabilityService gatewayObservabilityService = Mockito.mock(GatewayObservabilityService.class);
        GatewayRequestLifecycleService gatewayRequestLifecycleService = Mockito.mock(GatewayRequestLifecycleService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        CredentialMaterialResolver credentialMaterialResolver = Mockito.mock(CredentialMaterialResolver.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = new GatewayRequestFeatureService();
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        TranslationExecutionPlanCompiler translationExecutionPlanCompiler = realCompiler(
                routeSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService
        );
        GatewayChatRuntime runtime = Mockito.mock(GatewayChatRuntime.class);
        GatewayProperties gatewayProperties = new GatewayProperties();

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

        var selection = selectionResultFor(openAiCandidate());
        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-gemini-fileid-block-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selection);
        Mockito.when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any())).thenReturn(nativeCapabilityReport());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.executeGatewayResponse(geminiFileIdRequest())
        );

        assertTrue(exception.getMessage().contains("content.file.provider_file_id"));
        assertTrue(exception.getMessage().contains("native_route_required"));
        Mockito.verify(upstreamCredentialRepository, Mockito.never()).findById(Mockito.anyLong());
        Mockito.verify(credentialMaterialResolver, Mockito.never()).resolve(Mockito.any(), Mockito.any());
        Mockito.verify(runtime, Mockito.never()).execute(Mockito.any());
        Mockito.verify(routeSelectionService, Mockito.never()).markCredentialCooldown(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    void shouldPreserveRawResponseWhenEnrichingGatewayResponse() throws Exception {
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
        var rawResponse = objectMapper.readTree("""
                {
                  "id": "resp_raw_service_1",
                  "object": "response",
                  "model": "gpt-4.1-mini",
                  "output_text": "raw ok",
                  "output": [{"type": "message"}]
                }
                """);

        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public ExecutionBackend backend() {
                return ExecutionBackend.NATIVE;
            }

            @Override
            public boolean supports(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public CanonicalResponse execute(GatewayChatRuntimeContext context) {
                return new CanonicalResponse(
                        "resp_raw_service_1",
                        context.selectionResult().publicModel(),
                        "raw ok",
                        null,
                        List.of(),
                        CanonicalUsage.empty(),
                        com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.STOP,
                        rawResponse
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

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-raw-service-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.any(CanonicalRequest.class), Mockito.any(), Mockito.any()))
                .thenReturn(canonicalCompilation("openai", "/v1/responses", "gpt-4o"));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.RESPONSE,
                        TranslationOperation.RESPONSE_CREATE,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
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

        var response = service.executeGatewayResponse(responsesRequest());

        assertEquals("req-raw-service-1", response.response().requestId());
        assertEquals("resp_raw_service_1", response.response().rawResponse().path("id").asText());
        assertEquals("gpt-4.1-mini", response.response().rawResponse().path("model").asText());
    }

    @Test
    void shouldCapChatFallbackAttemptsFromRuntimePolicy() {
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
        RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService = Mockito.mock(RoutingPolicyRuntimeConfigService.class);
        GatewayProperties gatewayProperties = new GatewayProperties();

        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public ExecutionBackend backend() {
                return ExecutionBackend.NATIVE;
            }

            @Override
            public boolean supports(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public CanonicalResponse execute(GatewayChatRuntimeContext context) {
                throw new IllegalStateException("upstream 503");
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
                gatewayProperties,
                routingPolicyRuntimeConfigService
        );

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-chat-policy-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(routingPolicyRuntimeConfigService.maxAttempts(3, 2)).thenReturn(1);
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.any(CanonicalRequest.class), Mockito.any(), Mockito.any()))
                .thenReturn(canonicalCompilation("openai", "/v1/chat/completions", "gpt-4o"));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
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

        assertThrows(IllegalStateException.class, () -> service.executeGatewayResponse(canonicalRequest()));

        Mockito.verify(routeSelectionService).markCredentialCooldown(101L, "upstream 503");
        Mockito.verify(upstreamCredentialRepository, Mockito.never()).findById(202L);
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
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
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

    @Test
    void shouldApplyCloudCliFilterAndRouteWithClientFamily() {
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
        gatewayProperties.getCli().getRequestFilter().setEnabled(true);
        GatewayProperties.Cli.Rule rule = new GatewayProperties.Cli.Rule();
        rule.setId("mask-secret");
        rule.setAction("mask");
        rule.setClientFamilies(List.of("CURSOR"));
        rule.setRole("user");
        rule.setContains("secret");
        gatewayProperties.getCli().getRequestFilter().setRules(List.of(rule));
        AtomicReference<RouteSelectionRequest> capturedRouteRequest = new AtomicReference<>();

        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public ExecutionBackend backend() {
                return ExecutionBackend.NATIVE;
            }

            @Override
            public boolean supports(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public CanonicalResponse execute(GatewayChatRuntimeContext context) {
                return new CanonicalResponse(
                        null,
                        context.selectionResult().publicModel(),
                        "cursor ok",
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

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-cli-filter-1");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenAnswer(invocation -> {
            RouteSelectionRequest request = invocation.getArgument(0);
            capturedRouteRequest.set(request);
            return selectionResultWithFallbackCandidates();
        });
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.any(CanonicalRequest.class), Mockito.any(), Mockito.any()))
                .thenReturn(canonicalCompilation("openai", "/v1/chat/completions", "gpt-4o"));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
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

        CanonicalRequest request = canonicalRequest("hello secret");
        var response = service.executeGatewayResponse(request, GatewayClientFamily.CURSOR);

        assertEquals("cursor ok", response.response().outputText());
        assertEquals(GatewayClientFamily.CURSOR, capturedRouteRequest.get().clientFamily());
        assertTrue(capturedRouteRequest.get().requestBody().toString().contains("[FILTERED]"));
        assertTrue(capturedRouteRequest.get().requestBody().toString().contains("mask-secret"));
    }

    @Test
    void shouldRecordChatTraceMetadataSourceAndWireLimitations() {
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
        GatewayRequestTraceDetailService traceDetailService = Mockito.mock(GatewayRequestTraceDetailService.class);
        GatewayProperties gatewayProperties = new GatewayProperties();

        GatewayChatRuntime runtime = new GatewayChatRuntime() {
            @Override
            public ExecutionBackend backend() {
                return ExecutionBackend.NATIVE;
            }

            @Override
            public boolean supports(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
                return true;
            }

            @Override
            public CanonicalResponse execute(GatewayChatRuntimeContext context) {
                return new CanonicalResponse(
                        null,
                        context.selectionResult().publicModel(),
                        "trace ok",
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
                gatewayProperties,
                null,
                traceDetailService
        );

        Mockito.when(gatewayObservabilityService.nextRequestId()).thenReturn("req-chat-trace-source");
        Mockito.when(routeSelectionService.select(Mockito.any())).thenReturn(selectionResultWithFallbackCandidates());
        Mockito.when(translationExecutionPlanCompiler.compileSelected(Mockito.any(), Mockito.any(CanonicalRequest.class), Mockito.any(), Mockito.any()))
                .thenReturn(canonicalCompilation("openai", "/v1/chat/completions", "gpt-4o"));
        Mockito.when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        Mockito.when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential(101L)));
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

        var response = service.executeGatewayResponse(canonicalRequest());

        assertEquals("trace ok", response.response().outputText());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(traceDetailService, Mockito.atLeastOnce()).record(
                Mockito.eq("req-chat-trace-source"),
                Mockito.eq(RequestTraceStage.UPSTREAM_REQUEST),
                Mockito.eq(RequestTraceDirection.UPSTREAM),
                Mockito.eq(RequestTraceContentKind.JSON),
                Mockito.any(),
                metadataCaptor.capture()
        );
        Map<String, ?> metadata = metadataCaptor.getValue();
        assertEquals("gateway_constructed_upstream_request_summary", metadata.get("payloadSource"));
        assertEquals("structured gateway/runtime summary, not raw upstream HTTP wire body", metadata.get("wireBodyLimitation"));
        assertFalse((Boolean) metadata.get("wireBody"));
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
        return service(
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
                gatewayChatRuntimes,
                gatewayProperties,
                null
        );
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
            GatewayProperties gatewayProperties,
            RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService) {
        return service(
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
                gatewayChatRuntimes,
                gatewayProperties,
                routingPolicyRuntimeConfigService,
                null
        );
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
            GatewayProperties gatewayProperties,
            RoutingPolicyRuntimeConfigService routingPolicyRuntimeConfigService,
            GatewayRequestTraceDetailService traceDetailService) {
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
                gatewayProperties,
                routingPolicyRuntimeConfigService,
                null,
                new CloudCliRequestFilterService(),
                traceDetailService
        );
    }

    private CanonicalRequest canonicalRequest() {
        return canonicalRequest("hello");
    }

    private CanonicalRequest canonicalRequest(String content) {
        return new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text(content)))),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private CanonicalRequest responsesRequest() throws Exception {
        return new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "writer-fast",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello responses")))),
                List.of(),
                null,
                null,
                null,
                null,
                objectMapper.readTree("{\"model\":\"writer-fast\",\"input\":\"hello responses\",\"stream\":false}")
        );
    }

    private CanonicalRequest openAiChatFileIdRequest() throws Exception {
        return new OpenAiChatCompletionRequestMapper(objectMapper).toCanonicalRequest(
                "sk-gw-test",
                objectMapper.readTree("""
                        {
                          "model":"claude-sonnet-4",
                          "messages":[
                            {
                              "role":"user",
                              "content":[
                                {"type":"text","text":"总结这个 provider file"},
                                {"type":"input_file","input_file":{"file_id":"file-123","mime_type":"application/pdf","filename":"doc.pdf"}}
                              ]
                            }
                          ]
                        }
                        """)
        );
    }

    private CanonicalRequest geminiFileIdRequest() throws Exception {
        return new GeminiGenerateContentRequestMapper(objectMapper).toCanonicalRequest(
                "sk-gw-test",
                "gpt-4o",
                objectMapper.readTree("""
                        {
                          "contents":[
                            {
                              "role":"user",
                              "parts":[
                                {"text":"总结这个 Gemini file"},
                                {"fileData":{"mimeType":"application/pdf","fileId":"files/doc-123"}}
                              ]
                            }
                          ]
                        }
                        """),
                false
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

    private RouteSelectionResult selectionResultFor(com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView candidate) {
        RouteCandidateView selected = new RouteCandidateView(candidate, 11L, 10, 100, "NATIVE", 3);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                candidate.modelName(),
                candidate.modelName(),
                candidate.modelKey(),
                candidate.supportedProtocols().isEmpty() ? "openai" : candidate.supportedProtocols().getFirst(),
                "prefix-hash",
                "fingerprint",
                candidate.modelName(),
                GatewayClientFamily.GENERIC_OPENAI,
                List.of(),
                null,
                RouteSelectionSource.WEIGHTED_HASH,
                selected,
                List.of(selected),
                List.of(new RouteCandidateEvaluation(selected, true, "HEALTHY", null, false, RouteSelectionSource.WEIGHTED_HASH, 100d, List.of(), List.of())),
                List.of()
        );
    }

    private com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView anthropicCandidate() {
        return new com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView(
                303L,
                "anthropic-direct",
                ProviderType.ANTHROPIC_DIRECT,
                33L,
                ProviderFamily.ANTHROPIC,
                UpstreamSiteKind.ANTHROPIC_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.ANTHROPIC_V1_MESSAGES,
                ErrorSchemaStrategy.ANTHROPIC_ERROR,
                "https://api.anthropic.com",
                "claude-sonnet-4",
                "claude-sonnet-4",
                List.of("anthropic_native"),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                ReasoningTransport.ANTHROPIC,
                InteropCapabilityLevel.NATIVE
        );
    }

    private com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView openAiCandidate() {
        return new com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView(
                404L,
                "openai-direct",
                ProviderType.OPENAI_DIRECT,
                44L,
                ProviderFamily.OPENAI,
                UpstreamSiteKind.OPENAI_DIRECT,
                AuthStrategy.BEARER,
                PathStrategy.OPENAI_V1,
                ErrorSchemaStrategy.OPENAI_ERROR,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai"),
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                false,
                ReasoningTransport.OPENAI_CHAT,
                InteropCapabilityLevel.NATIVE
        );
    }

    private TranslationExecutionPlanCompiler realCompiler(
            GatewayRouteSelectionService routeSelectionService,
            GatewayRequestFeatureService gatewayRequestFeatureService,
            SiteCapabilityTruthService siteCapabilityTruthService) {
        return new TranslationExecutionPlanCompiler(
                routeSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                NonChatRoutePolicyService.forTests(siteCapabilityTruthService, new com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService()),
                NonChatTargetResolutionService.createDefault(),
                new NonChatDegradationPolicyService(),
                new LosslessTranslationMatrixService()
        );
    }

    private CapabilityResolutionReport nativeCapabilityReport() {
        java.util.LinkedHashMap<String, CapabilityResolution> resolutions = new java.util.LinkedHashMap<>();
        for (InteropFeature feature : InteropFeature.values()) {
            resolutions.put(feature.wireName(), new CapabilityResolution(
                    feature,
                    InteropCapabilityLevel.NATIVE,
                    InteropCapabilityLevel.NATIVE,
                    InteropCapabilityLevel.NATIVE,
                    InteropCapabilityLevel.NATIVE,
                    List.of(),
                    List.of()
            ));
        }
        return new CapabilityResolutionReport(
                java.util.Map.copyOf(resolutions),
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                ExecutionKind.NATIVE,
                "direct_upstream_execution",
                List.of(),
                List.of()
        );
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

    private CanonicalExecutionPlanCompilation blockedCanonicalCompilation(String reason) {
        return new CanonicalExecutionPlanCompilation(
                new CanonicalExecutionPlan(
                        false,
                        CanonicalIngressProtocol.RESPONSES,
                        "/v1/responses",
                        "/v1/responses",
                        "responses",
                        "writer-fast",
                        "writer-fast",
                        "writer-fast",
                        TranslationResourceType.RESPONSE,
                        TranslationOperation.RESPONSE_CREATE,
                        ExecutionKind.BLOCKED,
                        ExecutionBackend.NATIVE,
                        com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus.BLOCKED,
                        "blocked",
                        List.of(ExecutionBackend.NATIVE),
                        "test",
                        InteropCapabilityLevel.UNSUPPORTED,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.UNSUPPORTED,
                        List.of(reason),
                        List.of(InteropFeature.RESPONSE_OBJECT),
                        java.util.Map.of("response_object", InteropCapabilityLevel.NATIVE),
                        List.of(),
                        List.of(reason)
                ),
                selectionResultWithFallbackCandidates(),
                new GatewayRequestSemantics(
                        TranslationResourceType.RESPONSE,
                        TranslationOperation.RESPONSE_CREATE,
                        List.of(InteropFeature.RESPONSE_OBJECT),
                        true
                ),
                canonicalRequest()
        );
    }
}

