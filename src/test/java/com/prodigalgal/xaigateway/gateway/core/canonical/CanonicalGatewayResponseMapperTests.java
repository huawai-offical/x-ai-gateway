package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayResponse;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageCompleteness;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageSource;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalGatewayResponseMapperTests {

    private final CanonicalGatewayResponseMapper mapper = new CanonicalGatewayResponseMapper();

    @Test
    void shouldMapGatewayResponseAndStreamEventToCanonicalModels() {
        GatewayResponse response = new GatewayResponse(
                "req-1",
                selectionResult(),
                "hello",
                usageView(),
                List.of(new GatewayToolCall("call_1", "function", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                "step 1",
                GatewayFinishReason.TOOL_CALLS,
                null
        );

        CanonicalResponse canonicalResponse = mapper.toCanonicalResponse(response);
        assertEquals("req-1", canonicalResponse.requestId());
        assertEquals("hello", canonicalResponse.outputText());
        assertEquals("step 1", canonicalResponse.reasoning());
        assertEquals("lookup_weather", canonicalResponse.toolCalls().get(0).name());
        assertEquals(true, canonicalResponse.usage().present());

        GatewayStreamEvent event = new GatewayStreamEvent(
                GatewayStreamEventType.TEXT_DELTA,
                "hel",
                null,
                List.of(),
                GatewayUsageView.empty(),
                false,
                null,
                null,
                null,
                null
        );
        CanonicalStreamEvent canonicalStreamEvent = mapper.toCanonicalStreamEvent(event);
        assertEquals(CanonicalStreamEventType.TEXT_DELTA, canonicalStreamEvent.type());
        assertEquals("hel", canonicalStreamEvent.textDelta());
    }

    private RouteSelectionResult selectionResult() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "canonical-test",
                ProviderType.OPENAI_DIRECT,
                "https://api.example.com",
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
        RouteCandidateView routeCandidateView = new RouteCandidateView(candidate, 1L, 10, 100);
        return new RouteSelectionResult(
                1L,
                "sk-gw-test",
                "gpt-4o-mini",
                "gpt-4o-mini",
                "gpt-4o-mini",
                "openai",
                "prefix",
                "fingerprint",
                "gpt-4o-mini",
                RouteSelectionSource.PREFIX_AFFINITY,
                routeCandidateView,
                List.of(routeCandidateView)
        );
    }

    private GatewayUsageView usageView() {
        return new GatewayUsageView(
                100,
                40,
                20,
                5,
                10,
                2,
                10,
                2,
                50,
                null,
                60,
                GatewayUsageCompleteness.FINAL,
                GatewayUsageSource.DIRECT_RESPONSE,
                null
        );
    }
}
