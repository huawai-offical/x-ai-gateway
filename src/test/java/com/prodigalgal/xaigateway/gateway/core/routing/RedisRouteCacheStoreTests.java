package com.prodigalgal.xaigateway.gateway.core.routing;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.shared.ReasoningTransport;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class RedisRouteCacheStoreTests {

    private final StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final GatewayProperties gatewayProperties = new GatewayProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldReadRouteSnapshot() throws Exception {
        RedisRouteCacheStore store = new RedisRouteCacheStore(stringRedisTemplate, objectMapper, gatewayProperties);
        GatewayRequestSemantics semantics = semantics();
        RoutePlanSnapshot snapshot = snapshot();
        Mockito.when(valueOperations.get(any()))
                .thenReturn(objectMapper.writeValueAsString(snapshot));

        var result = store.get(1L, "openai", "/v1/chat/completions", "gpt-4o", semantics);

        assertTrue(result.isPresent());
        assertEquals("gpt-4o", result.get().resolvedModelKey());
    }

    @Test
    void shouldWriteRouteSnapshotWithTtl() {
        RedisRouteCacheStore store = new RedisRouteCacheStore(stringRedisTemplate, objectMapper, gatewayProperties);
        GatewayRequestSemantics semantics = semantics();

        store.put(1L, "openai", "/v1/chat/completions", "gpt-4o", semantics, snapshot(), Duration.ofMinutes(2));

        Mockito.verify(valueOperations).set(any(String.class), any(String.class), eq(Duration.ofMinutes(2)));
    }

    private GatewayRequestSemantics semantics() {
        return new GatewayRequestSemantics(
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                List.of(InteropFeature.CHAT_TEXT),
                true
        );
    }

    private RoutePlanSnapshot snapshot() {
        CatalogCandidateView candidate = new CatalogCandidateView(
                101L,
                "candidate",
                ProviderType.OPENAI_DIRECT,
                "https://example.com",
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
        return new RoutePlanSnapshot("gpt-4o", "gpt-4o", "gpt-4o", List.of(evaluation));
    }
}
