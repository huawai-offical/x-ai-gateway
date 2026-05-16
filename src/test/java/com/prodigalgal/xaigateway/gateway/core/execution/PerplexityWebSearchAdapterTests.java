package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerplexityWebSearchAdapterTests {

    private final PerplexityWebSearchAdapter adapter = new PerplexityWebSearchAdapter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMapGatewayWebSearchPayloadToPerplexitySonarShape() {
        ObjectNode gatewayPayload = objectMapper.createObjectNode();
        gatewayPayload.put("model", "sonar");
        gatewayPayload.put("query", "latest provider adapter news");
        gatewayPayload.put("search_recency_filter", "month");
        gatewayPayload.put("search_after_date_filter", "05/01/2026");
        gatewayPayload.put("stream_mode", "concise");
        gatewayPayload.put("reasoning_effort", "medium");
        gatewayPayload.put("language_preference", "zh");
        gatewayPayload.putObject("web_search_options").put("search_context_size", "high");
        gatewayPayload.putArray("search_domain_filter").add("docs.perplexity.ai");

        ObjectNode upstream = adapter.toUpstreamPayload(gatewayPayload, "sonar-pro");

        assertEquals("/v1/sonar", adapter.upstreamPath());
        assertEquals("sonar-pro", upstream.path("model").asText());
        assertEquals("user", upstream.path("messages").get(0).path("role").asText());
        assertEquals("latest provider adapter news", upstream.path("messages").get(0).path("content").asText());
        assertEquals("month", upstream.path("search_recency_filter").asText());
        assertEquals("05/01/2026", upstream.path("search_after_date_filter").asText());
        assertEquals("concise", upstream.path("stream_mode").asText());
        assertEquals("medium", upstream.path("reasoning_effort").asText());
        assertEquals("zh", upstream.path("language_preference").asText());
        assertEquals("high", upstream.path("web_search_options").path("search_context_size").asText());
        assertEquals("docs.perplexity.ai", upstream.path("search_domain_filter").get(0).asText());
    }

    @Test
    void shouldOnlyApplyToPerplexityWebSearchRoute() {
        assertTrue(adapter.supports(UpstreamSiteKind.PERPLEXITY, "/v1/web_search"));
        assertFalse(adapter.supports(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, "/v1/web_search"));
        assertFalse(adapter.supports(UpstreamSiteKind.PERPLEXITY, "/v1/chat/completions"));
    }
}
