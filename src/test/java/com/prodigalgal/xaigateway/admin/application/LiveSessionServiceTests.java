package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.LiveSessionCreateRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionEventRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionRuntimeEventRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSessionServiceTests {

    @Test
    void shouldCreateSessionAppendAudioMetricsAndReplaySse() {
        LiveSessionRepository sessionRepository = Mockito.mock(LiveSessionRepository.class);
        LiveSessionEventRepository eventRepository = Mockito.mock(LiveSessionEventRepository.class);
        LiveSessionService service = service(sessionRepository, eventRepository);

        Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 11L);
            }
            return entity;
        });
        Mockito.when(eventRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEventEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 21L);
            return entity;
        });

        var created = service.create(new LiveSessionCreateRequest(9L, "gpt-live", null, null, 600L));
        LiveSessionEntity session = Mockito.mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (LiveSessionEntity) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        Mockito.when(sessionRepository.findBySessionKey(created.sessionKey())).thenReturn(Optional.of(session));

        var input = service.appendEvent(created.sessionKey(), new LiveSessionEventRequest("audio.delta", "input", "{\"ok\":true}", 128L));
        service.appendEvent(created.sessionKey(), new LiveSessionEventRequest("audio.done", "output", "{\"done\":true}", 256L));

        assertEquals(1L, input.eventId());
        assertEquals(2L, session.getEventCount());
        assertEquals(128L, session.getInputAudioBytes());
        assertEquals(256L, session.getOutputAudioBytes());

        LiveSessionEventEntity replayEvent = new LiveSessionEventEntity();
        replayEvent.setSession(session);
        replayEvent.setEventId(2L);
        replayEvent.setEventType("audio.done");
        replayEvent.setDirection("OUTPUT");
        replayEvent.setPayloadJson("{\"done\":true}");
        replayEvent.setAudioBytes(256L);
        Mockito.when(eventRepository.findAllBySession_IdAndEventIdGreaterThanOrderByEventIdAsc(11L, 1L))
                .thenReturn(List.of(replayEvent));

        String sse = service.replaySse(created.sessionKey(), 1L);
        assertTrue(sse.contains("id: 2"));
        assertTrue(sse.contains("event: audio.done"));
    }

    @Test
    void shouldConnectHeartbeatResumeCloseAndExchangeProviderRuntimeEvents() {
        LiveSessionRepository sessionRepository = Mockito.mock(LiveSessionRepository.class);
        LiveSessionEventRepository eventRepository = Mockito.mock(LiveSessionEventRepository.class);
        LiveSessionService service = service(sessionRepository, eventRepository);

        Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 31L);
            }
            return entity;
        });
        Mockito.when(eventRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEventEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", entity.getEventId() + 100L);
            return entity;
        });

        var created = service.create(new LiveSessionCreateRequest(10L, "gemini-live-2.5", "gemini_live", "{\"tenant\":\"demo\"}", 900L));
        LiveSessionEntity session = Mockito.mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (LiveSessionEntity) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        Mockito.when(sessionRepository.findBySessionKey(created.sessionKey())).thenReturn(Optional.of(session));
        Mockito.when(sessionRepository.findByResumeToken(created.resumeToken())).thenReturn(Optional.of(session));

        var connected = service.connect(created.sessionKey());
        assertEquals("CONNECTED", connected.status());
        assertTrue(connected.metadataJson().contains("gemini-live-runtime"));
        assertTrue(connected.metadataJson().contains("upstreamResumeHandle"));
        assertTrue(connected.metadataJson().contains("connectionPoolLeaseId"));
        assertTrue(connected.metadataJson().contains("connectionPoolTenant"));

        var streamed = service.sendRuntimeEvent(created.sessionKey(), new LiveSessionRuntimeEventRequest(
                "audio.delta",
                "{\"transcript\":\"你好\"}",
                512L
        ));
        assertEquals("STREAMING", streamed.status());
        assertEquals(512L, streamed.inputAudioBytes());
        assertEquals(512L, streamed.outputAudioBytes());
        assertEquals(3L, streamed.eventCount());
        assertTrue(streamed.metadataJson().contains("lastRuntimeEventAt"));

        var heartbeat = service.heartbeat(created.sessionKey());
        assertEquals("CONNECTED", heartbeat.status());
        assertTrue(heartbeat.metadataJson().contains("lastHeartbeatAt"));

        var resumed = service.resume(created.resumeToken());
        assertEquals("CONNECTED", resumed.status());
        assertTrue(resumed.metadataJson().contains("RESUMED"));

        var closed = service.close(created.sessionKey());
        assertEquals("CLOSED", closed.status());
        assertTrue(closed.metadataJson().contains("closedAt"));
        assertTrue(closed.metadataJson().contains("\"connectionPoolState\":\"RELEASED\""));

        assertEquals(6L, session.getEventCount());
        assertEquals(512L, session.getInputAudioBytes());
        assertEquals(512L, session.getOutputAudioBytes());
    }

    @Test
    void shouldRejectRuntimeProtocolWithoutAdapter() {
        LiveSessionRepository sessionRepository = Mockito.mock(LiveSessionRepository.class);
        LiveSessionEventRepository eventRepository = Mockito.mock(LiveSessionEventRepository.class);
        LiveSessionService service = new LiveSessionService(
                sessionRepository,
                eventRepository,
                new ObjectMapper(),
                List.of()
        );

        LiveSessionEntity session = new LiveSessionEntity();
        ReflectionTestUtils.setField(session, "id", 41L);
        session.setSessionKey("live_missing");
        session.setResumeToken("resume_missing");
        session.setModelName("unknown-live");
        session.setProtocol("missing_live");
        session.setStatus("CREATED");
        session.setMetadataJson("{}");
        Mockito.when(sessionRepository.findBySessionKey("live_missing")).thenReturn(Optional.of(session));

        try {
            service.connect("live_missing");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("runtime adapter"));
            return;
        }
        throw new AssertionError("缺失 runtime adapter 时应该拒绝连接。");
    }

    @Test
    void shouldSummarizeMockRealtimeConformance() {
        LiveSessionRepository sessionRepository = Mockito.mock(LiveSessionRepository.class);
        LiveSessionEventRepository eventRepository = Mockito.mock(LiveSessionEventRepository.class);
        LiveSessionService service = service(sessionRepository, eventRepository);

        Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 51L);
            }
            return entity;
        });
        Mockito.when(eventRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEventEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", entity.getEventId() + 200L);
            return entity;
        });

        var created = service.create(new LiveSessionCreateRequest(12L, "mock-live", "mock_realtime", "{}", 600L));
        LiveSessionEntity session = Mockito.mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (LiveSessionEntity) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        Mockito.when(sessionRepository.findBySessionKey(created.sessionKey())).thenReturn(Optional.of(session));

        service.connect(created.sessionKey());
        service.sendRuntimeEvent(created.sessionKey(), new LiveSessionRuntimeEventRequest("audio.delta", "{\"text\":\"hi\"}", 64L));
        service.close(created.sessionKey());
        LiveSessionEventEntity connected = event(session, 1L, "runtime.connected", "OUTPUT", "{}", 0L);
        LiveSessionEventEntity input = event(session, 2L, "audio.delta", "INPUT", "{}", 64L);
        LiveSessionEventEntity output = event(session, 3L, "provider.audio.delta", "OUTPUT", "{}", 64L);
        LiveSessionEventEntity closed = event(session, 4L, "runtime.closed", "OUTPUT", "{}", 0L);
        Mockito.when(eventRepository.findAllBySession_IdOrderByEventIdAsc(51L))
                .thenReturn(List.of(connected, input, output, closed));
        Mockito.when(eventRepository.findAllBySession_IdAndEventIdGreaterThanOrderByEventIdAsc(51L, 0L))
                .thenReturn(List.of(connected, input, output, closed));

        var conformance = service.conformance(created.sessionKey());

        assertEquals("PASS", conformance.conformanceStatus());
        assertTrue(conformance.connected());
        assertTrue(conformance.streaming());
        assertTrue(conformance.closed());
        assertTrue(conformance.sseReplayAvailable());
        assertEquals(1L, conformance.inputEventCount());
        assertEquals(3L, conformance.outputEventCount());
    }

    @Test
    void shouldExposeWebSocketTransportConformanceForMockAdapter() {
        LiveSessionRepository sessionRepository = Mockito.mock(LiveSessionRepository.class);
        LiveSessionEventRepository eventRepository = Mockito.mock(LiveSessionEventRepository.class);
        LiveSessionService service = service(sessionRepository, eventRepository);

        Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 61L);
            }
            return entity;
        });
        Mockito.when(eventRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new LiveSessionCreateRequest(13L, "mock-ws-live", "mock_websocket_realtime", "{}", 600L));
        LiveSessionEntity session = Mockito.mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (LiveSessionEntity) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        Mockito.when(sessionRepository.findBySessionKey(created.sessionKey())).thenReturn(Optional.of(session));

        service.connect(created.sessionKey());
        service.sendRuntimeEvent(created.sessionKey(), new LiveSessionRuntimeEventRequest("audio.delta", "{\"text\":\"hi\"}", 96L));
        service.heartbeat(created.sessionKey());
        service.close(created.sessionKey());

        LiveSessionEventEntity connected = event(session, 1L, "websocket.connected", "OUTPUT", "{}", 0L);
        LiveSessionEventEntity input = event(session, 2L, "audio.delta", "INPUT", "{}", 96L);
        LiveSessionEventEntity frame = event(session, 3L, "websocket.frame.audio.delta", "OUTPUT", "{}", 96L);
        LiveSessionEventEntity pong = event(session, 4L, "websocket.pong", "OUTPUT", "{}", 0L);
        LiveSessionEventEntity closed = event(session, 5L, "websocket.closed", "OUTPUT", "{}", 0L);
        Mockito.when(eventRepository.findAllBySession_IdOrderByEventIdAsc(61L))
                .thenReturn(List.of(connected, input, frame, pong, closed));
        Mockito.when(eventRepository.findAllBySession_IdAndEventIdGreaterThanOrderByEventIdAsc(61L, 0L))
                .thenReturn(List.of(connected, input, frame, pong, closed));

        var conformance = service.conformance(created.sessionKey());

        assertEquals("websocket", conformance.transport());
        assertEquals("PASS", conformance.conformanceStatus());
        assertTrue(conformance.checks().contains("websocket frames available"));
        assertTrue(session.getMetadataJson().contains("websocketState"));
    }

    @Test
    void shouldExposeOpenAiRealtimeProviderWebSocketConformance() {
        LiveSessionRepository sessionRepository = Mockito.mock(LiveSessionRepository.class);
        LiveSessionEventRepository eventRepository = Mockito.mock(LiveSessionEventRepository.class);
        LiveSessionService service = service(sessionRepository, eventRepository);

        Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 71L);
            }
            return entity;
        });
        Mockito.when(eventRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new LiveSessionCreateRequest(14L, "gpt-4o-realtime-preview", "openai_realtime", "{}", 600L));
        LiveSessionEntity session = Mockito.mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (LiveSessionEntity) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        Mockito.when(sessionRepository.findBySessionKey(created.sessionKey())).thenReturn(Optional.of(session));

        var connected = service.connect(created.sessionKey());
        service.sendRuntimeEvent(created.sessionKey(), new LiveSessionRuntimeEventRequest("audio.delta", "{\"text\":\"hi\"}", 128L));
        service.heartbeat(created.sessionKey());
        service.close(created.sessionKey());

        LiveSessionEventEntity connectedEvent = event(session, 1L, "websocket.connected", "OUTPUT", "{}", 0L);
        LiveSessionEventEntity input = event(session, 2L, "audio.delta", "INPUT", "{}", 128L);
        LiveSessionEventEntity frame = event(session, 3L, "websocket.frame.audio.delta", "OUTPUT", "{}", 128L);
        LiveSessionEventEntity pong = event(session, 4L, "websocket.pong", "OUTPUT", "{}", 0L);
        LiveSessionEventEntity closed = event(session, 5L, "websocket.closed", "OUTPUT", "{}", 0L);
        Mockito.when(eventRepository.findAllBySession_IdOrderByEventIdAsc(71L))
                .thenReturn(List.of(connectedEvent, input, frame, pong, closed));
        Mockito.when(eventRepository.findAllBySession_IdAndEventIdGreaterThanOrderByEventIdAsc(71L, 0L))
                .thenReturn(List.of(connectedEvent, input, frame, pong, closed));

        var conformance = service.conformance(created.sessionKey());

        assertTrue(connected.metadataJson().contains("openai-realtime-runtime"));
        assertTrue(connected.metadataJson().contains("wss://api.openai.com/v1/realtime"));
        assertEquals("websocket", conformance.transport());
        assertEquals("PASS", conformance.conformanceStatus());
        assertTrue(conformance.checks().contains("websocket frames available"));
    }

    @Test
    void shouldNormalizeRealtimeErrorsRetryAndCloseSemantics() {
        LiveSessionRepository sessionRepository = Mockito.mock(LiveSessionRepository.class);
        LiveSessionEventRepository eventRepository = Mockito.mock(LiveSessionEventRepository.class);
        LiveSessionService service = service(sessionRepository, eventRepository);
        List<LiveSessionEventEntity> savedEvents = new ArrayList<>();

        Mockito.when(sessionRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 81L);
            }
            return entity;
        });
        Mockito.when(eventRepository.save(Mockito.any())).thenAnswer(invocation -> {
            LiveSessionEventEntity entity = invocation.getArgument(0);
            savedEvents.add(entity);
            return entity;
        });

        var created = service.create(new LiveSessionCreateRequest(15L, "gpt-4o-realtime-preview", "openai_realtime", "{}", 600L));
        LiveSessionEntity session = Mockito.mockingDetails(sessionRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (LiveSessionEntity) invocation.getArgument(0))
                .findFirst()
                .orElseThrow();
        Mockito.when(sessionRepository.findBySessionKey(created.sessionKey())).thenReturn(Optional.of(session));

        service.connect(created.sessionKey());
        service.sendRuntimeEvent(created.sessionKey(), new LiveSessionRuntimeEventRequest("audio.delta", "{\"chunk\":\"base64\"}", 320L));
        var timeout = service.sendRuntimeEvent(created.sessionKey(), new LiveSessionRuntimeEventRequest("error.timeout", "{\"error\":{\"code\":\"timeout\"}}", 0L));
        var retry = service.sendRuntimeEvent(created.sessionKey(), new LiveSessionRuntimeEventRequest("session.retry", "{\"attempt\":2}", 0L));
        var closed = service.close(created.sessionKey());

        Mockito.when(eventRepository.findAllBySession_IdOrderByEventIdAsc(81L)).thenReturn(savedEvents);
        Mockito.when(eventRepository.findAllBySession_IdAndEventIdGreaterThanOrderByEventIdAsc(81L, 0L)).thenReturn(savedEvents);
        var conformance = service.conformance(created.sessionKey());

        assertTrue(timeout.metadataJson().contains("UPSTREAM_TIMEOUT"));
        assertTrue(retry.metadataJson().contains("retryAfterMs"));
        assertTrue(closed.metadataJson().contains("gateway_close_as_client_cancel"));
        assertTrue(savedEvents.stream().anyMatch(event -> "websocket.error".equals(event.getEventType())));
        assertTrue(savedEvents.stream().anyMatch(event -> "websocket.retry".equals(event.getEventType())));
        assertTrue(conformance.checks().contains("binary audio frames accounted"));
        assertTrue(conformance.checks().contains("provider errors normalized"));
        assertTrue(conformance.checks().contains("retry semantics available"));
    }

    private LiveSessionEventEntity event(
            LiveSessionEntity session,
            long eventId,
            String eventType,
            String direction,
            String payloadJson,
            long audioBytes) {
        LiveSessionEventEntity event = new LiveSessionEventEntity();
        event.setSession(session);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setDirection(direction);
        event.setPayloadJson(payloadJson);
        event.setAudioBytes(audioBytes);
        return event;
    }

    private LiveSessionService service(
            LiveSessionRepository sessionRepository,
            LiveSessionEventRepository eventRepository) {
        return new LiveSessionService(
                sessionRepository,
                eventRepository,
                new ObjectMapper(),
                List.of(new GeminiLiveRuntimeAdapter(), new OpenAiRealtimeRuntimeAdapter(), new MockRealtimeRuntimeAdapter(), new MockWebSocketRealtimeRuntimeAdapter())
        );
    }
}
