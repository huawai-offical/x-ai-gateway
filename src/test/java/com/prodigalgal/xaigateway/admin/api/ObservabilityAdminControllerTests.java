package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ObservabilityQueryService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = ObservabilityAdminController.class)
@Import(PermitAllSecurityTestConfig.class)
class ObservabilityAdminControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ObservabilityQueryService observabilityQueryService;

    @Test
    void shouldReturnRouteDecisionLogs() {
        Instant from = Instant.parse("2026-04-07T07:00:00Z");
        Instant to = Instant.parse("2026-04-07T09:00:00Z");
        Mockito.when(observabilityQueryService.listRouteDecisions(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(List.of(new RouteDecisionLogResponse(
                        1L,
                        "req-1",
                        1L,
                        "sk-gw-test",
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        "openai",
                        "gpt-4o",
                        "PREFIX_AFFINITY",
                        101L,
                        ProviderType.OPENAI_DIRECT,
                        "https://api.openai.com",
                        "prefix",
                        "fingerprint",
                        1,
                        "{\"candidates\":[]}",
                        Instant.parse("2026-04-07T08:00:00Z")
                )));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/observability/route-decisions")
                        .queryParam("distributedKeyId", 1)
                        .queryParam("providerType", "OPENAI_DIRECT")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].requestId").isEqualTo("req-1")
                .jsonPath("$[0].selectionSource").isEqualTo("PREFIX_AFFINITY");
    }

    @Test
    void shouldReturnCacheHitLogs() {
        Instant from = Instant.parse("2026-04-07T07:00:00Z");
        Instant to = Instant.parse("2026-04-07T09:00:00Z");
        Mockito.when(observabilityQueryService.listCacheHits(null, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(List.of(new CacheHitLogResponse(
                        1L,
                        "req-1",
                        1L,
                        "openai",
                        ProviderType.OPENAI_DIRECT,
                        101L,
                        "gpt-4o",
                        "prefix",
                        "fingerprint",
                        "prompt_cache",
                        300,
                        0,
                        300,
                        null,
                        Instant.parse("2026-04-07T08:00:00Z")
                )));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/observability/cache-hits")
                        .queryParam("providerType", "OPENAI_DIRECT")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].cacheKind").isEqualTo("prompt_cache")
                .jsonPath("$[0].cacheHitTokens").isEqualTo(300);
    }

    @Test
    void shouldReturnUpstreamCacheReferences() {
        Instant from = Instant.parse("2026-04-07T07:00:00Z");
        Instant to = Instant.parse("2026-04-07T09:00:00Z");
        Mockito.when(observabilityQueryService.listUpstreamCacheReferences(1L, ProviderType.GEMINI_DIRECT, "ACTIVE", from, to))
                .thenReturn(List.of(new UpstreamCacheReferenceResponse(
                        1L,
                        1L,
                        ProviderType.GEMINI_DIRECT,
                        301L,
                        "gemini-2.5-pro",
                        "prefix",
                        "cachedContents/abc",
                        "ACTIVE",
                        Instant.parse("2026-04-07T09:00:00Z"),
                        Instant.parse("2026-04-07T08:30:00Z"),
                        Instant.parse("2026-04-07T08:00:00Z"),
                        Instant.parse("2026-04-07T08:30:00Z")
                )));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/observability/upstream-cache-references")
                        .queryParam("distributedKeyId", 1)
                        .queryParam("providerType", "GEMINI_DIRECT")
                        .queryParam("status", "ACTIVE")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].externalCacheRef").isEqualTo("cachedContents/abc")
                .jsonPath("$[0].status").isEqualTo("ACTIVE");
    }

    @Test
    void shouldReturnObservabilitySummary() {
        Instant from = Instant.parse("2026-04-07T07:00:00Z");
        Instant to = Instant.parse("2026-04-07T09:00:00Z");
        Mockito.when(observabilityQueryService.summary(1L, ProviderType.OPENAI_DIRECT, from, to))
                .thenReturn(new ObservabilitySummaryResponse(
                        from,
                        to,
                        12,
                        6,
                        2,
                        1800,
                        120,
                        1800
                ));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/observability/summary")
                        .queryParam("distributedKeyId", 1)
                        .queryParam("providerType", "OPENAI_DIRECT")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sampledFrom").isEqualTo("2026-04-07T07:00:00Z")
                .jsonPath("$.sampledRouteDecisionCount").isEqualTo(12)
                .jsonPath("$.totalCacheHitTokens").isEqualTo(1800)
                .jsonPath("$.sampledActiveUpstreamCacheReferenceCount").isEqualTo(2);
    }
}
