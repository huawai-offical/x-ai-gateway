package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.admin.api.LiveSessionCreateRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionResponse;
import com.prodigalgal.xaigateway.admin.api.LiveSessionRuntimeEventRequest;
import com.prodigalgal.xaigateway.admin.application.LiveSessionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiRealtimeWebSocketBridgeTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldOpenLiveSessionAndBuildSessionCreatedEvent() throws Exception {
        GatewayTokenAuthenticationResolver resolver = mock(GatewayTokenAuthenticationResolver.class);
        LiveSessionService liveSessionService = mock(LiveSessionService.class);
        OpenAiRealtimeWebSocketBridge bridge = new OpenAiRealtimeWebSocketBridge(resolver, liveSessionService, objectMapper);
        HttpHeaders headers = headers();

        when(resolver.authenticate("Bearer sk-gw-test.secret", null, null, null))
                .thenReturn(new AuthenticatedDistributedKey(9L, "sk-gw-test", "test"));
        when(liveSessionService.create(any())).thenReturn(response("live_1", "gpt-realtime-mini", "CREATED"));
        when(liveSessionService.connect("live_1")).thenReturn(response("live_1", "gpt-realtime-mini", "CONNECTED"));

        OpenAiRealtimeWebSocketContext context = bridge.open(headers, URI.create("http://127.0.0.1/v1/realtime?model=gpt-realtime-mini"));
        String event = bridge.sessionCreated(context);

        var root = objectMapper.readTree(event);
        assertEquals("session.created", root.path("type").asText());
        assertEquals("live_1", root.path("session").path("id").asText());
        assertEquals("realtime.session", root.path("session").path("object").asText());
        assertEquals("gpt-realtime-mini", root.path("session").path("model").asText());

        ArgumentCaptor<LiveSessionCreateRequest> captor = ArgumentCaptor.forClass(LiveSessionCreateRequest.class);
        verify(liveSessionService).create(captor.capture());
        assertEquals(9L, captor.getValue().distributedKeyId());
        assertEquals("openai_realtime", captor.getValue().protocol());
        assertTrue(captor.getValue().metadataJson().contains("openai_realtime_websocket"));
    }

    @Test
    void shouldForwardSessionUpdateAndReturnSessionUpdated() throws Exception {
        LiveSessionService liveSessionService = mock(LiveSessionService.class);
        OpenAiRealtimeWebSocketBridge bridge = new OpenAiRealtimeWebSocketBridge(
                mock(GatewayTokenAuthenticationResolver.class),
                liveSessionService,
                objectMapper
        );
        OpenAiRealtimeWebSocketContext context = context();
        when(liveSessionService.sendRuntimeEvent(eq("live_1"), any()))
                .thenReturn(response("live_1", "gpt-realtime", "STREAMING"));

        List<String> responses = bridge.acceptText(context, """
                {"event_id":"evt_client_1","type":"session.update","session":{"instructions":"be brief"}}
                """);

        assertEquals(1, responses.size());
        var root = objectMapper.readTree(responses.get(0));
        assertEquals("session.updated", root.path("type").asText());
        assertEquals("be brief", root.path("session").path("client_update").path("instructions").asText());

        ArgumentCaptor<LiveSessionRuntimeEventRequest> captor = ArgumentCaptor.forClass(LiveSessionRuntimeEventRequest.class);
        verify(liveSessionService).sendRuntimeEvent(eq("live_1"), captor.capture());
        assertEquals("session.update", captor.getValue().eventType());
        assertTrue(captor.getValue().payloadJson().contains("evt_client_1"));
    }

    @Test
    void shouldRejectInvalidOrMissingTypeWithoutForwarding() throws Exception {
        LiveSessionService liveSessionService = mock(LiveSessionService.class);
        OpenAiRealtimeWebSocketBridge bridge = new OpenAiRealtimeWebSocketBridge(
                mock(GatewayTokenAuthenticationResolver.class),
                liveSessionService,
                objectMapper
        );

        var invalid = objectMapper.readTree(bridge.acceptText(context(), "{").get(0));
        var missingType = objectMapper.readTree(bridge.acceptText(context(), "{\"event_id\":\"evt_missing\"}").get(0));

        assertEquals("error", invalid.path("type").asText());
        assertEquals("error", missingType.path("type").asText());
        assertEquals("type", missingType.path("error").path("param").asText());
        verify(liveSessionService, never()).sendRuntimeEvent(eq("live_1"), any());
    }

    @Test
    void shouldEstimateAudioBytesFromBase64AudioField() {
        LiveSessionService liveSessionService = mock(LiveSessionService.class);
        OpenAiRealtimeWebSocketBridge bridge = new OpenAiRealtimeWebSocketBridge(
                mock(GatewayTokenAuthenticationResolver.class),
                liveSessionService,
                objectMapper
        );
        when(liveSessionService.sendRuntimeEvent(eq("live_1"), any()))
                .thenReturn(response("live_1", "gpt-realtime", "STREAMING"));

        bridge.acceptText(context(), "{\"type\":\"input_audio_buffer.append\",\"audio\":\"AQID\"}");

        ArgumentCaptor<LiveSessionRuntimeEventRequest> captor = ArgumentCaptor.forClass(LiveSessionRuntimeEventRequest.class);
        verify(liveSessionService).sendRuntimeEvent(eq("live_1"), captor.capture());
        assertEquals(3L, captor.getValue().audioBytes());
    }

    @Test
    void shouldCloseLiveSession() {
        LiveSessionService liveSessionService = mock(LiveSessionService.class);
        OpenAiRealtimeWebSocketBridge bridge = new OpenAiRealtimeWebSocketBridge(
                mock(GatewayTokenAuthenticationResolver.class),
                liveSessionService,
                objectMapper
        );

        bridge.close(context());

        verify(liveSessionService).close("live_1");
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer sk-gw-test.secret");
        return headers;
    }

    private OpenAiRealtimeWebSocketContext context() {
        return new OpenAiRealtimeWebSocketContext("live_1", 9L, "gpt-realtime", new AtomicLong(0L));
    }

    private LiveSessionResponse response(String sessionKey, String model, String status) {
        Instant now = Instant.parse("2026-05-16T02:00:00Z");
        return new LiveSessionResponse(
                1L,
                sessionKey,
                9L,
                model,
                "openai_realtime",
                status,
                "resume_1",
                0L,
                0L,
                0L,
                0L,
                "{}",
                now.plusSeconds(600),
                null,
                now,
                now
        );
    }
}
