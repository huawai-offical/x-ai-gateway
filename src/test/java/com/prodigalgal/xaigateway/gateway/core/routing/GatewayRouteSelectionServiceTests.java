package com.prodigalgal.xaigateway.gateway.core.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedCredentialBindingView;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyView;
import com.prodigalgal.xaigateway.gateway.core.cache.AffinityCacheService;
import com.prodigalgal.xaigateway.gateway.core.cache.PromptFingerprintService;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.catalog.ModelCatalogQueryService;
import com.prodigalgal.xaigateway.gateway.core.catalog.ResolvedModelView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
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

        GatewayProperties properties = new GatewayProperties();
        PromptFingerprintService promptFingerprintService = new PromptFingerprintService(new ObjectMapper(), properties);

        GatewayRouteSelectionService service = new GatewayRouteSelectionService(
                distributedKeyQueryService,
                modelCatalogQueryService,
                promptFingerprintService,
                affinityCacheService
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
        when(modelCatalogQueryService.resolveRequestedModel("gpt-4o", "openai"))
                .thenReturn(Optional.of(new ResolvedModelView(
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        false,
                        List.of(candidate)
                )));
        when(affinityCacheService.getPrefixAffinity(eq(1L), eq("OPENAI_DIRECT"), eq("gpt-4o"), anyString()))
                .thenReturn("101");

        RouteSelectionResult result = service.select(new RouteSelectionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                Map.of("messages", List.of(Map.of("role", "user", "content", "hello")))
        ));

        assertEquals(RouteSelectionSource.PREFIX_AFFINITY, result.selectionSource());
        assertEquals(101L, result.selectedCandidate().candidate().credentialId());
        assertNotNull(result.prefixHash());
        assertNotNull(result.fingerprint());
    }
}
