package com.prodigalgal.xaigateway.gateway.core.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsEventBusServiceTests {

    @Test
    void shouldPublishEventIntoStream() {
        OpsEventBusService service = new OpsEventBusService(new ObjectMapper());
        service.publish(OpsEventType.TRAFFIC_SNAPSHOT, java.util.Map.of("qps", 1));
        String payload = service.stream().blockFirst();
        assertTrue(payload.contains("traffic_snapshot"));
        assertTrue(payload.contains("\"qps\":1"));
    }
}
