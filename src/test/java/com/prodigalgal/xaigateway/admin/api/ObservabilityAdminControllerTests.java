package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.ObservabilityQueryService;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.testsupport.PermitAllSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
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
        Mockito.when(observabilityQueryService.listRouteDecisions(
                        1L,
                        ProviderType.OPENAI_DIRECT,
                        from,
                        to,
                        null,
                        null,
                        null))
                .thenReturn(List.of(new RouteDecisionLogResponse(
                        1L,
                        "req-1",
                        1L,
                        "sk-gw-test",
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        "openai",
                        "/v1/chat/completions",
                        "chat",
                        "chat_completion",
                        "gpt-4o",
                        "PREFIX_AFFINITY",
                        "NATIVE",
                        "NATIVE",
                        "NATIVE",
                        "chat",
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
                .jsonPath("$[0].selectionSource").isEqualTo("PREFIX_AFFINITY")
                .jsonPath("$[0].supportStatus").isEqualTo("NATIVE");
    }

    @Test
    void shouldReturnCacheHitLogs() {
        Instant from = Instant.parse("2026-04-07T07:00:00Z");
        Instant to = Instant.parse("2026-04-07T09:00:00Z");
        Mockito.when(observabilityQueryService.listCacheHits(
                        null,
                        ProviderType.OPENAI_DIRECT,
                        from,
                        to,
                        null,
                        null,
                        null))
                .thenReturn(List.of(new CacheHitLogResponse(
                        1L,
                        "req-1",
                        1L,
                        "openai",
                        "/v1/chat/completions",
                        "chat",
                        "chat_completion",
                        ProviderType.OPENAI_DIRECT,
                        101L,
                        "gpt-4o",
                        "prefix",
                        "fingerprint",
                        "prompt_cache",
                        "NATIVE",
                        "NATIVE",
                        "NATIVE",
                        "chat",
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
                .jsonPath("$[0].cacheHitTokens").isEqualTo(300)
                .jsonPath("$[0].supportStatus").isEqualTo("NATIVE");
    }

    @Test
    void shouldReturnRequestLogs() {
        Instant from = Instant.parse("2026-04-07T07:00:00Z");
        Instant to = Instant.parse("2026-04-07T09:00:00Z");
        Mockito.when(observabilityQueryService.listRequestLogs(
                        1L,
                        ProviderType.OPENAI_DIRECT,
                        from,
                        to,
                        null,
                        null,
                        null))
                .thenReturn(List.of(new RequestLogResponse(
                        1L,
                        "req-1",
                        1L,
                        "sk-gw-test",
                        "openai",
                        "/v1/files/file_123/content",
                        "file",
                        "file_content_get",
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        "gpt-4o",
                        ProviderType.OPENAI_DIRECT,
                        101L,
                        "PREFIX_AFFINITY",
                        "NATIVE",
                        "DEGRADED",
                        "LOSSY",
                        "resource-orchestration",
                        "batch_1",
                        "binary",
                        "file.content",
                        "file_123",
                        "completed",
                        2,
                        com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus.COMPLETED,
                        from,
                        to,
                        from,
                        820L,
                        null,
                        null
                )));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/observability/request-logs")
                        .queryParam("distributedKeyId", 1)
                        .queryParam("providerType", "OPENAI_DIRECT")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].requestId").isEqualTo("req-1")
                .jsonPath("$[0].supportStatus").isEqualTo("DEGRADED")
                .jsonPath("$[0].gatewayResourceKey").isEqualTo("batch_1")
                .jsonPath("$[0].responseObjectType").isEqualTo("file.content")
                .jsonPath("$[0].canonicalEventCount").isEqualTo(2);
    }

    @Test
    void shouldReturnUpstreamCacheReferences() {
        Instant from = Instant.parse("2026-04-07T07:00:00Z");
        Instant to = Instant.parse("2026-04-07T09:00:00Z");
        Mockito.when(observabilityQueryService.listUpstreamCacheReferences(
                        1L,
                        ProviderType.GEMINI_DIRECT,
                        "ACTIVE",
                        from,
                        to,
                        null,
                        null,
                        null))
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
    void shouldReturnTrace() {
        Instant now = Instant.parse("2026-04-07T08:00:00Z");
        Mockito.when(observabilityQueryService.trace("req-1"))
                .thenReturn(new ObservabilityTraceResponse(
                        new RequestLogResponse(
                                1L,
                                "req-1",
                                1L,
                                "sk-gw-test",
                                "openai",
                                "/v1/batches/batch_1",
                                "batch",
                                "batch_get",
                                "gpt-4o",
                                "gpt-4o",
                                "gpt-4o",
                                "gpt-4o",
                                ProviderType.OPENAI_DIRECT,
                                101L,
                                "PREFIX_AFFINITY",
                                "ORCHESTRATION",
                                "NATIVE",
                                "NATIVE",
                                "gateway-object-lineage",
                                "batch_1",
                                "object",
                                "batch",
                                "batch_1",
                                "in_progress",
                                1,
                                com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestStatus.COMPLETED,
                                now,
                                now,
                                now,
                                120L,
                                null,
                                null
                        ),
                        new RouteDecisionLogResponse(
                                2L,
                                "req-1",
                                1L,
                                "sk-gw-test",
                                "gpt-4o",
                                "gpt-4o",
                                "gpt-4o",
                                "openai",
                                "/v1/batches/batch_1",
                                "batch",
                                "batch_get",
                                "gpt-4o",
                                "PREFIX_AFFINITY",
                                "ORCHESTRATION",
                                "NATIVE",
                                "NATIVE",
                                "gateway-object-lineage",
                                101L,
                                ProviderType.OPENAI_DIRECT,
                                "https://api.openai.com",
                                "prefix",
                                "fingerprint",
                                1,
                                "{\"candidates\":[]}",
                                now
                        ),
                        List.of(),
                        List.of(),
                        new AsyncResourceSummaryResponse(
                                "batch_1",
                                GatewayAsyncResourceType.BATCH,
                                "in_progress",
                                "IN_PROGRESS",
                                false,
                                false,
                                "gateway-object-lineage",
                                "batch_1",
                                1,
                                null,
                                null,
                                null,
                                now,
                                now
                        ),
                        null
                ));

        webTestClient.get()
                .uri("/admin/observability/traces/req-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestLog.requestId").isEqualTo("req-1")
                .jsonPath("$.routeDecision.selectionSource").isEqualTo("PREFIX_AFFINITY")
                .jsonPath("$.asyncResourceSummary.resourceKey").isEqualTo("batch_1");
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
                        5,
                        4,
                        1,
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
                .jsonPath("$.sampledUsageRecordCount").isEqualTo(5)
                .jsonPath("$.sampledFinalUsageRecordCount").isEqualTo(4)
                .jsonPath("$.totalCacheHitTokens").isEqualTo(1800)
                .jsonPath("$.sampledActiveUpstreamCacheReferenceCount").isEqualTo(2);
    }
}
