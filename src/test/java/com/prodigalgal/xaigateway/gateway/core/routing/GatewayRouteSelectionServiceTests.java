package com.prodigalgal.xaigateway.gateway.core.routing;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.api.CostEstimateResponse;
import com.prodigalgal.xaigateway.admin.application.CostRoutingService;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.cache.AffinityCacheService;
import com.prodigalgal.xaigateway.gateway.core.cache.PromptFingerprintService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.ResolvedModelView;
import com.prodigalgal.xaigateway.gateway.core.execution.ExecutionBackendPolicyService;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngine;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolution;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionReport;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyService;
import com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyDecision;
import com.prodigalgal.xaigateway.gateway.core.interop.RouteSelectionMode;
import com.prodigalgal.xaigateway.gateway.core.interop.SupportStatus;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyCandidateDecision;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolvedModel;
import com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolver;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class GatewayRouteSelectionServiceTests {

    @Test
    void shouldRouteWithModelPolicyMappedUpstreamModel() {
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        NonChatRoutePolicyService nonChatRoutePolicyService = Mockito.mock(NonChatRoutePolicyService.class);
        RouteCacheStore routeCacheStore = Mockito.mock(RouteCacheStore.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);
        ModelPolicyResolver modelPolicyResolver = Mockito.mock(ModelPolicyResolver.class);
        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), new GatewayProperties());
        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                distributedKeyGovernanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                nonChatRoutePolicyService,
                GovernancePolicyEngine.allowAll(),
                routeCacheStore,
                healthStateStore,
                null,
                null,
                modelPolicyResolver
        );
        DistributedKeyView keyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("responses"),
                List.of("gpt-5-codex"),
                List.of(new DistributedCredentialBindingView(
                        11L,
                        101L,
                        "mimo",
                        ProviderType.OPENAI_COMPATIBLE,
                        "https://token-plan-sgp.xiaomimimo.com/v1",
                        10,
                        100
                ))
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "mimo",
                ProviderType.OPENAI_COMPATIBLE,
                "https://token-plan-sgp.xiaomimimo.com/v1",
                "mimo-v2.5-pro",
                "mimo-v2.5-pro",
                List.of("responses", "openai"),
                true,
                true,
                false,
                false,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView routeCandidate = new RouteCandidateView(candidate, 11L, 10, 100);

        when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(keyView));
        when(distributedKeyGovernanceService.evaluate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceDecision(List.of(), List.of(), 1L, 100L, null));
        when(modelPolicyResolver.hasEnabledPolicies()).thenReturn(true);
        when(modelPolicyResolver.resolveRequestedModel(keyView, "responses", "gpt-5-codex"))
                .thenReturn(new ModelPolicyResolvedModel(
                        "gpt-5-codex",
                        "gpt-5-codex",
                        "mimo-v2.5-pro",
                        "gpt-5-codex",
                        true,
                        List.of(candidate),
                        List.of("model_policy_mapping:DISTRIBUTED_KEY:1")
                ));
        when(modelPolicyResolver.evaluateCandidate(Mockito.eq(keyView), Mockito.eq("responses"), Mockito.eq("gpt-5-codex"), Mockito.eq("gpt-5-codex"), Mockito.any()))
                .thenReturn(new ModelPolicyCandidateDecision(routeCandidate, true, List.of(), List.of("allow_policy=1")));
        when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.RESPONSE,
                        TranslationOperation.RESPONSE_CREATE,
                        List.of(InteropFeature.RESPONSE_OBJECT),
                        true
                ));
        when(nonChatRoutePolicyService.evaluateCandidate(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new NonChatRoutePolicyDecision(
                        RouteSelectionMode.CATALOG_SELECTION,
                        ExecutionBackend.NATIVE,
                        List.of(ExecutionBackend.NATIVE),
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        SupportStatus.NATIVE,
                        "chat",
                        List.of(),
                        List.of(),
                        "test"
                ));
        when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(new com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity() {{
            setProviderType(ProviderType.OPENAI_COMPATIBLE);
            setBaseUrl("https://token-plan-sgp.xiaomimimo.com/v1");
        }}));
        when(healthStateStore.getCredentialState(101L)).thenReturn(Optional.empty());
        when(accountSelectionService.hasHealthyAccountBinding(1L, ProviderType.OPENAI_COMPATIBLE, GatewayClientFamily.CODEX))
                .thenReturn(true);

        RouteSelectionResult result = service.select(new RouteSelectionRequest(
                "sk-gw-test",
                "responses",
                "/v1/responses",
                "gpt-5-codex",
                Map.of("input", "hello"),
                GatewayClientFamily.CODEX,
                false
        ));

        assertEquals("gpt-5-codex", result.publicModel());
        assertEquals("mimo-v2.5-pro", result.resolvedModelKey());
        assertEquals("gpt-5-codex", result.modelGroup());
        assertTrue(result.candidateEvaluations().get(0).scoreBreakdown().contains("allow_policy=1"));
    }

    @Test
    void shouldPreferPrefixAffinityWhenPresent() {
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        RouteCacheStore routeCacheStore = Mockito.mock(RouteCacheStore.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);

        GatewayProperties properties = new GatewayProperties();
        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), properties);

        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                distributedKeyGovernanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                routeCacheStore,
                healthStateStore
        );

        DistributedKeyView keyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "sk-gw-test...masked",
                List.of("openai"),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        11L,
                        101L,
                        "openai-primary",
                        ProviderType.OPENAI_DIRECT,
                        "https://api.openai.com",
                        10,
                        100
                ))
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai-primary",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai"),
                true,
                false,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );

        when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(keyView));
        when(distributedKeyGovernanceService.evaluate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceDecision(List.of(), List.of(), 1L, 1000L, null));
        when(accountSelectionService.hasHealthyAccountBinding(1L, ProviderType.OPENAI_DIRECT, GatewayClientFamily.GENERIC_OPENAI))
                .thenReturn(true);
        when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(new com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity() {{
            setProviderType(ProviderType.OPENAI_DIRECT);
            setBaseUrl("https://api.openai.com");
        }}));
        when(modelCatalogQueryService.resolveRequestedModel("gpt-4o", "openai"))
                .thenReturn(Optional.of(new ResolvedModelView(
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        false,
                        List.of(candidate)
                )));
        when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature.CHAT_TEXT),
                        true
                ));
        when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any()))
                .thenReturn(new CapabilityResolutionReport(
                        Map.of("chat_text", new CapabilityResolution(
                                InteropFeature.CHAT_TEXT,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                List.of(),
                                List.of()
                        )),
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        ExecutionKind.NATIVE,
                        "direct_upstream_execution",
                        List.of(),
                        List.of()
                ));
        when(routeCacheStore.get(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.empty());
        when(healthStateStore.getCredentialState(101L)).thenReturn(Optional.empty());
        when(affinityCacheService.getPrefixAffinity(eq(1L), eq("OPENAI_DIRECT"), eq("gpt-4o"), anyString()))
                .thenReturn("101");

        RouteSelectionResult result = service.select(new RouteSelectionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))),
                GatewayClientFamily.GENERIC_OPENAI,
                false
        ));

        assertEquals(RouteSelectionSource.PREFIX_AFFINITY, result.selectionSource());
        assertEquals(101L, result.selectedCandidate().candidate().credentialId());
        assertNotNull(result.prefixHash());
        assertNotNull(result.fingerprint());
        assertEquals("HEALTHY", result.candidateEvaluations().get(0).healthState());
        assertTrue(result.candidateEvaluations().get(0).scoreBreakdown().contains("priority_score=100.0"));
        assertTrue(result.candidateEvaluations().get(0).scoreBreakdown().contains("weight_score=100.0"));
        assertTrue(result.candidateEvaluations().get(0).scoreBreakdown().contains("affinity_bonus=500.0"));
        assertTrue(result.candidateEvaluations().get(0).scoreBreakdown().stream().anyMatch(item -> item.startsWith("total_score=")));
        assertTrue(result.candidateEvaluations().get(0).scoreBreakdown().contains("retry_candidate=true"));
        assertTrue(result.candidateEvaluations().get(0).scoreBreakdown().contains("fallback_order=priority:10,weight:100"));
    }

    @Test
    void shouldBlockCandidateWhenCooldownIsActive() {
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        RouteCacheStore routeCacheStore = Mockito.mock(RouteCacheStore.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);

        GatewayProperties properties = new GatewayProperties();
        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), properties);

        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                distributedKeyGovernanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                routeCacheStore,
                healthStateStore
        );

        DistributedKeyView keyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "sk-gw-test...masked",
                List.of("openai"),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        11L,
                        101L,
                        "openai-primary",
                        ProviderType.OPENAI_DIRECT,
                        "https://api.openai.com",
                        10,
                        100
                ))
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai-primary",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o",
                "gpt-4o",
                List.of("openai"),
                true,
                false,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );

        when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(keyView));
        when(distributedKeyGovernanceService.evaluate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceDecision(List.of(), List.of(), 1L, 1000L, null));
        when(accountSelectionService.hasHealthyAccountBinding(1L, ProviderType.OPENAI_DIRECT, GatewayClientFamily.GENERIC_OPENAI))
                .thenReturn(true);
        when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(new com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity() {{
            setProviderType(ProviderType.OPENAI_DIRECT);
            setBaseUrl("https://api.openai.com");
        }}));
        when(modelCatalogQueryService.resolveRequestedModel("gpt-4o", "openai"))
                .thenReturn(Optional.of(new ResolvedModelView(
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        false,
                        List.of(candidate)
                )));
        when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any()))
                .thenReturn(new CapabilityResolutionReport(
                        Map.of("chat_text", new CapabilityResolution(
                                InteropFeature.CHAT_TEXT,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                InteropCapabilityLevel.NATIVE,
                                List.of(),
                                List.of()
                        )),
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        InteropCapabilityLevel.NATIVE,
                        ExecutionKind.NATIVE,
                        "direct_upstream_execution",
                        List.of(),
                        List.of()
                ));
        when(routeCacheStore.get(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.empty());
        when(healthStateStore.getCredentialState(101L))
                .thenReturn(Optional.of(new CredentialHealthState("COOLDOWN", "status=503", Instant.now().plusSeconds(300))));

        assertThrows(IllegalArgumentException.class, () -> service.select(new RouteSelectionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))),
                GatewayClientFamily.GENERIC_OPENAI,
                false
        )));
    }

    @Test
    void shouldNotBypassGovernanceWhenRouteCacheHits() throws Exception {
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        RouteCacheStore routeCacheStore = Mockito.mock(RouteCacheStore.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);

        GatewayProperties properties = new GatewayProperties();
        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), properties);
        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                distributedKeyGovernanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                routeCacheStore,
                healthStateStore
        );

        DistributedKeyView keyView = new DistributedKeyView(
                1L, "test-key", "sk-gw-test", "masked", List.of("openai"), List.of(), List.of()
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L, "candidate", ProviderType.OPENAI_DIRECT, "https://api.openai.com", "gpt-4o", "gpt-4o",
                List.of("openai"), true, true, true, true, true, true, ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 11L, 10, 100, "NATIVE", 3);
        RouteCandidateEvaluation evaluation = new RouteCandidateEvaluation(
                routeCandidateView,
                true,
                "STATIC_READY",
                null,
                false,
                RouteSelectionSource.WEIGHTED_HASH,
                0d,
                List.of(),
                List.of()
        );

        when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(keyView));
        when(distributedKeyGovernanceService.evaluate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceDecision(List.of("当前 DistributedKey 已超过 RPM 限制。"), List.of(), 1L, 1000L, null));
        when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        when(routeCacheStore.get(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.of(new RoutePlanSnapshot("gpt-4o", "gpt-4o", "gpt-4o", List.of(evaluation))));

        assertThrows(IllegalArgumentException.class, () -> service.select(new RouteSelectionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))),
                GatewayClientFamily.GENERIC_OPENAI,
                true
        )));
    }

    @Test
    void shouldAllowGeminiUploadSurfaceSelectionWhenFileObjectIsAvailable() {
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        RouteCacheStore routeCacheStore = Mockito.mock(RouteCacheStore.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);

        GatewayProperties properties = new GatewayProperties();
        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), properties);
        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                distributedKeyGovernanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                routeCacheStore,
                healthStateStore
        );

        DistributedKeyView keyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        11L,
                        201L,
                        "gemini-primary",
                        ProviderType.GEMINI_DIRECT,
                        "https://generativelanguage.googleapis.com",
                        10,
                        100
                ))
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                201L,
                "gemini-primary",
                ProviderType.GEMINI_DIRECT,
                "https://generativelanguage.googleapis.com",
                "gemini-2.5-pro",
                "gemini-2.5-pro",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );

        when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(keyView));
        when(distributedKeyGovernanceService.evaluate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceDecision(List.of(), List.of(), 1L, 1000L, null));
        when(accountSelectionService.hasHealthyAccountBinding(1L, ProviderType.GEMINI_DIRECT, GatewayClientFamily.GENERIC_OPENAI))
                .thenReturn(true);
        when(upstreamCredentialRepository.findById(201L)).thenReturn(Optional.of(new com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity() {{
            setProviderType(ProviderType.GEMINI_DIRECT);
            setBaseUrl("https://generativelanguage.googleapis.com");
        }}));
        when(modelCatalogQueryService.resolveRequestedModel("gemini-2.5-pro", "openai"))
                .thenReturn(Optional.of(new ResolvedModelView(
                        "gemini-2.5-pro",
                        "gemini-2.5-pro",
                        "gemini-2.5-pro",
                        false,
                        List.of(candidate)
                )));
        when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.UPLOAD,
                        TranslationOperation.UPLOAD_CREATE,
                        List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT),
                        true
                ));
        when(siteCapabilityTruthService.resolve(Mockito.any(), Mockito.any()))
                .thenReturn(new CapabilityResolutionReport(
                        Map.of(
                                "upload_create", new CapabilityResolution(
                                        InteropFeature.UPLOAD_CREATE,
                                        InteropCapabilityLevel.UNSUPPORTED,
                                        InteropCapabilityLevel.NATIVE,
                                        InteropCapabilityLevel.UNSUPPORTED,
                                        InteropCapabilityLevel.UNSUPPORTED,
                                        List.of("Gemini Files API 存在，但不等价于 OpenAI /v1/uploads 的 create/parts/complete/cancel contract，因此仅开放 gateway-local orchestration surface。"),
                                        List.of()
                                ),
                                "file_object", new CapabilityResolution(
                                        InteropFeature.FILE_OBJECT,
                                        InteropCapabilityLevel.NATIVE,
                                        InteropCapabilityLevel.NATIVE,
                                        InteropCapabilityLevel.NATIVE,
                                        InteropCapabilityLevel.NATIVE,
                                        List.of(),
                                        List.of()
                                )
                        ),
                        InteropCapabilityLevel.UNSUPPORTED,
                        InteropCapabilityLevel.UNSUPPORTED,
                        InteropCapabilityLevel.UNSUPPORTED,
                        ExecutionKind.BLOCKED,
                        "blocked",
                        List.of("Gemini Files API 存在，但不等价于 OpenAI /v1/uploads 的 create/parts/complete/cancel contract，因此仅开放 gateway-local orchestration surface。"),
                        List.of()
                ));
        when(routeCacheStore.get(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.empty());
        when(healthStateStore.getCredentialState(201L)).thenReturn(Optional.empty());

        RouteSelectionResult result = service.select(new RouteSelectionRequest(
                "sk-gw-test",
                "openai",
                "/v1/uploads",
                "gemini-2.5-pro",
                Map.of("filename", "batch-input.jsonl", "purpose", "batch", "bytes", 1234),
                GatewayClientFamily.GENERIC_OPENAI,
                false
        ));

        assertEquals(201L, result.selectedCandidate().candidate().credentialId());
        assertTrue(result.candidateEvaluations().get(0).eligible());
        assertEquals("HEALTHY", result.candidateEvaluations().get(0).healthState());
    }

    @Test
    void shouldBlockRouteSelectionWhenCostGuardRejectsCandidate() {
        DistributedKeyQueryService distributedKeyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService distributedKeyGovernanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);
        RouteCacheStore routeCacheStore = Mockito.mock(RouteCacheStore.class);
        HealthStateStore healthStateStore = Mockito.mock(HealthStateStore.class);
        CostRoutingService costRoutingService = Mockito.mock(CostRoutingService.class);

        GatewayProperties properties = new GatewayProperties();
        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), properties);
        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                distributedKeyGovernanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService,
                NonChatRoutePolicyService.forTests(siteCapabilityTruthService, new ExecutionBackendPolicyService()),
                GovernancePolicyEngine.allowAll(),
                routeCacheStore,
                healthStateStore,
                costRoutingService
        );

        DistributedKeyView keyView = new DistributedKeyView(
                1L,
                "test-key",
                "sk-gw-test",
                "masked",
                List.of("openai"),
                List.of(),
                List.of(new DistributedCredentialBindingView(
                        11L,
                        101L,
                        "openai-primary",
                        ProviderType.OPENAI_DIRECT,
                        "https://api.openai.com",
                        10,
                        100
                ))
        );
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "openai-primary",
                ProviderType.OPENAI_DIRECT,
                "https://api.openai.com",
                "gpt-4o-mini",
                "gpt-4o-mini",
                List.of("openai"),
                true,
                true,
                true,
                true,
                true,
                true,
                ReasoningTransport.OPENAI_CHAT
        );
        RouteCandidateView routeCandidate = new RouteCandidateView(candidate, 11L, 10, 100, "NATIVE", 3);
        RouteCandidateEvaluation staticEvaluation = new RouteCandidateEvaluation(
                routeCandidate,
                true,
                "STATIC_READY",
                null,
                false,
                RouteSelectionSource.WEIGHTED_HASH,
                0D,
                List.of(),
                List.of()
        );

        when(distributedKeyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(keyView));
        when(distributedKeyGovernanceService.evaluate(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceDecision(List.of(), List.of(), 1L, 1_000L, null));
        when(gatewayRequestFeatureService.describe(Mockito.anyString(), Mockito.any()))
                .thenReturn(new GatewayRequestSemantics(
                        TranslationResourceType.CHAT,
                        TranslationOperation.CHAT_COMPLETION,
                        List.of(InteropFeature.CHAT_TEXT),
                        true
                ));
        when(routeCacheStore.get(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.of(new RoutePlanSnapshot("gpt-4o-mini", "gpt-4o-mini", "gpt-4o-mini", List.of(staticEvaluation))));
        when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(new com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity() {{
            setProviderType(ProviderType.OPENAI_DIRECT);
            setBaseUrl("https://api.openai.com");
        }}));
        when(healthStateStore.getCredentialState(101L)).thenReturn(Optional.empty());
        when(accountSelectionService.hasHealthyAccountBinding(1L, ProviderType.OPENAI_DIRECT, GatewayClientFamily.GENERIC_OPENAI))
                .thenReturn(true);
        when(costRoutingService.estimateCandidate(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(Optional.of(new CostEstimateResponse(
                        "OPENAI_DIRECT",
                        "gpt-4o-mini",
                        "USD",
                        1_000L,
                        2_000L,
                        0L,
                        700_000L,
                        "USD 0.700000",
                        100L,
                        300L,
                        20L,
                        1L,
                        "test-key",
                        9L,
                        200_000L,
                        3600,
                        null,
                        180_000L,
                        false,
                        List.of("超过分发 Key 预算上限。", "用户余额不足。")
                )));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.select(new RouteSelectionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o-mini",
                Map.of("messages", List.of(Map.of("role", "user", "content", "hello")), "max_tokens", 2_000),
                GatewayClientFamily.GENERIC_OPENAI,
                false
        )));

        assertTrue(error.getMessage().contains("cost_guard_blocked"));
        assertTrue(error.getMessage().contains("用户余额不足"));
        Mockito.verify(costRoutingService).estimateCandidate(
                eq("OPENAI_DIRECT"),
                eq("gpt-4o-mini"),
                eq(1L),
                eq(2_000L),
                eq(0L),
                eq(1L),
                isNull(),
                isNull());
    }
}
