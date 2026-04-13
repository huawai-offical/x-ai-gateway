package com.prodigalgal.xaigateway.gateway.core.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolution;
import com.prodigalgal.xaigateway.gateway.core.interop.CapabilityResolutionReport;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureService;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.repository.NetworkProxyRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamCredentialRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GatewayRouteSelectionServiceTests {

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
                siteCapabilityTruthService
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
    }
}
