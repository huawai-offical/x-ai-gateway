package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.LiveSessionCreateRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionEventRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionRuntimeEventRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.LiveSessionEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionEventRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.LiveSessionRepository;
import java.util.List;
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

    private LiveSessionService service(
            LiveSessionRepository sessionRepository,
            LiveSessionEventRepository eventRepository) {
        return new LiveSessionService(
                sessionRepository,
                eventRepository,
                new ObjectMapper(),
                List.of(new GeminiLiveRuntimeAdapter(), new OpenAiRealtimeRuntimeAdapter())
        );
    }
}
