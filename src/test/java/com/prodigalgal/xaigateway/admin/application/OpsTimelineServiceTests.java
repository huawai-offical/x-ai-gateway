package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpsProbeRunRequest;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsProbeRunEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.OpsSystemEventEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsProbeRunRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.OpsSystemEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsTimelineServiceTests {

    @Test
    void shouldPersistProbeRunAndCreateFilterableSystemEvent() {
        OpsProbeRunRepository probeRunRepository = Mockito.mock(OpsProbeRunRepository.class);
        OpsSystemEventRepository systemEventRepository = Mockito.mock(OpsSystemEventRepository.class);
        OpsTimelineService service = new OpsTimelineService(probeRunRepository, systemEventRepository, new ObjectMapper());

        Mockito.when(probeRunRepository.save(Mockito.any())).thenAnswer(invocation -> {
            OpsProbeRunEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 31L);
            return entity;
        });
        Mockito.when(systemEventRepository.save(Mockito.any())).thenAnswer(invocation -> {
            OpsSystemEventEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 41L);
            return entity;
        });

        var run = service.createProbeRun(new OpsProbeRunRequest("edge-probe", "https://gateway.local/health", "console", true, null));

        assertEquals("FAILED", run.status());
        assertEquals("ERROR", run.severity());
        assertTrue(run.errorMessage().contains("强制失败"));
        assertTrue(run.detailJson().contains("\"probeType\":\"forced\""));
        Mockito.verify(systemEventRepository).save(Mockito.argThat(event ->
                "OPS_PROBE_RUN".equals(event.getEventType()) && "ERROR".equals(event.getSeverity())));

        OpsSystemEventEntity event = new OpsSystemEventEntity();
        event.setEventType("OPS_PROBE_RUN");
        event.setSeverity("ERROR");
        event.setSource("console");
        event.setEntityType("ops_probe_run");
        event.setEntityRef("31");
        event.setTitle("拨测运行：edge-probe");
        event.setDetailJson("{}");
        event.setOccurredAt(Instant.now());
        Mockito.when(systemEventRepository.findTop500ByOrderByOccurredAtDesc()).thenReturn(List.of(event));

        var filtered = service.listEvents("ERROR", "console", null, null, null, null, null);
        assertEquals(1, filtered.size());
        assertTrue(filtered.get(0).title().contains("edge-probe"));
    }

    @Test
    void shouldFilterSystemEventsByEventTypeAndEntityRef() {
        OpsProbeRunRepository probeRunRepository = Mockito.mock(OpsProbeRunRepository.class);
        OpsSystemEventRepository systemEventRepository = Mockito.mock(OpsSystemEventRepository.class);
        OpsTimelineService service = new OpsTimelineService(probeRunRepository, systemEventRepository, new ObjectMapper());
        OpsSystemEventEntity codexEvent = new OpsSystemEventEntity();
        ReflectionTestUtils.setField(codexEvent, "id", 11L);
        codexEvent.setEventType("CODEX_RUNTIME_BATCH_RECOVERY");
        codexEvent.setSeverity("INFO");
        codexEvent.setSource("account-pool-admin");
        codexEvent.setEntityType("ACCOUNT_POOL");
        codexEvent.setEntityRef("account-pool:5");
        codexEvent.setTitle("Codex Runtime 批量恢复预检");
        codexEvent.setDetailJson("{}");
        codexEvent.setOccurredAt(Instant.parse("2026-05-08T01:00:00Z"));
        OpsSystemEventEntity otherEvent = new OpsSystemEventEntity();
        ReflectionTestUtils.setField(otherEvent, "id", 12L);
        otherEvent.setEventType("OTHER_EVENT");
        otherEvent.setSeverity("INFO");
        otherEvent.setSource("account-pool-admin");
        otherEvent.setEntityType("ACCOUNT_POOL");
        otherEvent.setEntityRef("account-pool:6");
        otherEvent.setTitle("Other");
        otherEvent.setDetailJson("{}");
        otherEvent.setOccurredAt(Instant.parse("2026-05-08T01:01:00Z"));
        Mockito.when(systemEventRepository.findTop500ByOrderByOccurredAtDesc()).thenReturn(List.of(otherEvent, codexEvent));

        var filtered = service.listEvents(null, null, "CODEX_RUNTIME_BATCH_RECOVERY", "ACCOUNT_POOL", "account-pool:5", null, null);

        assertEquals(1, filtered.size());
        assertEquals(11L, filtered.get(0).id());
        assertEquals("account-pool:5", filtered.get(0).entityRef());
    }
}
