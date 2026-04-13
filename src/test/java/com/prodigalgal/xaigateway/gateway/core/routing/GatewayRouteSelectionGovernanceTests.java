package com.prodigalgal.xaigateway.gateway.core.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyGovernanceService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.cache.AffinityCacheService;
import com.prodigalgal.xaigateway.gateway.core.cache.PromptFingerprintService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.ResolvedModelView;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolution;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionReport;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamCredentialEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class GatewayRouteSelectionGovernanceTests {

    @Test
    void shouldBlockExpiredDistributedKey() {
        DistributedKeyQueryService keyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService governanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);

        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), new GatewayProperties());
        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                keyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                governanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService
        );

        when(keyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(new DistributedKeyView(
                1L, "test", "sk-gw-test", "masked", List.of("openai"), List.of(), List.of(), Instant.now().minusSeconds(60),
                null, null, null, null, null, null, List.of(), false,
                List.of(new DistributedCredentialBindingView(11L, 101L, "openai", ProviderType.OPENAI_DIRECT, "https://api.openai.com", 10, 100))
        )));

        assertThrows(IllegalArgumentException.class, () -> service.select(new RouteSelectionRequest(
                "sk-gw-test", "openai", "/v1/chat/completions", "gpt-4o",
                Map.of("messages", List.of(Map.of("role", "user", "content", "hi"))),
                GatewayClientFamily.GENERIC_OPENAI, false
        )));
    }

    @Test
    void shouldBlockWhenClientFamilyDoesNotMatch() {
        DistributedKeyQueryService keyQueryService = Mockito.mock(DistributedKeyQueryService.class);
        ModelCatalogQueryService modelCatalogQueryService = Mockito.mock(ModelCatalogQueryService.class);
        AffinityCacheService affinityCacheService = Mockito.mock(AffinityCacheService.class);
        DistributedKeyGovernanceService governanceService = Mockito.mock(DistributedKeyGovernanceService.class);
        UpstreamCredentialRepository upstreamCredentialRepository = Mockito.mock(UpstreamCredentialRepository.class);
        NetworkProxyRepository networkProxyRepository = Mockito.mock(NetworkProxyRepository.class);
        AccountSelectionService accountSelectionService = Mockito.mock(AccountSelectionService.class);
        GatewayRequestFeatureService gatewayRequestFeatureService = Mockito.mock(GatewayRequestFeatureService.class);
        SiteCapabilityTruthService siteCapabilityTruthService = Mockito.mock(SiteCapabilityTruthService.class);

        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), new GatewayProperties());
        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                keyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService,
                governanceService,
                upstreamCredentialRepository,
                networkProxyRepository,
                accountSelectionService,
                gatewayRequestFeatureService,
                siteCapabilityTruthService
        );

        DistributedKeyView keyView = new DistributedKeyView(
                1L, "test", "sk-gw-test", "masked",
                List.of("openai"), List.of(), List.of(), null,
                null, null, null, null, null, null,
                List.of(GatewayClientFamily.CODEX.name()), true,
                List.of(new DistributedCredentialBindingView(11L, 101L, "openai", ProviderType.OPENAI_DIRECT, "https://api.openai.com", 10, 100))
        );
        when(keyQueryService.findActiveByKeyPrefix("sk-gw-test")).thenReturn(Optional.of(keyView));
        when(governanceService.evaluate(any(), any(), any(), Mockito.anyBoolean()))
                .thenReturn(new DistributedKeyGovernanceService.GovernanceDecision(List.of("当前 DistributedKey 不允许客户端家族 GEMINI_CLI。"), List.of(), 1, 1000, null));
        when(modelCatalogQueryService.resolveRequestedModel("gpt-4o", "openai")).thenReturn(Optional.of(new ResolvedModelView(
                "gpt-4o", "gpt-4o", "gpt-4o", false,
                List.of(new CatalogCandidateView(101L, "openai", ProviderType.OPENAI_DIRECT, "https://api.openai.com", "gpt-4o", "gpt-4o", List.of("openai"), true, false, false, false, false, false, ReasoningTransport.OPENAI_CHAT))
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
        UpstreamCredentialEntity credential = new UpstreamCredentialEntity();
        credential.setProviderType(ProviderType.OPENAI_DIRECT);
        credential.setBaseUrl("https://api.openai.com");
        when(upstreamCredentialRepository.findById(101L)).thenReturn(Optional.of(credential));
        when(accountSelectionService.hasHealthyAccountBinding(1L, ProviderType.OPENAI_DIRECT, GatewayClientFamily.GEMINI_CLI)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.select(new RouteSelectionRequest(
                "sk-gw-test", "openai", "/v1/chat/completions", "gpt-4o",
                Map.of("messages", List.of(Map.of("role", "user", "content", "hi"))),
                GatewayClientFamily.GEMINI_CLI, false
        )));
    }
}
