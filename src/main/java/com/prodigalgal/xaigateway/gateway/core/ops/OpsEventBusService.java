package com.prodigalgal.xaigateway.gateway.core.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OpsEventBusService {

    private final Sinks.Many<String> sink = Sinks.many().replay().limit(100);
    private final ObjectMapper objectMapper;

    public OpsEventBusService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void publish(OpsEventType eventType, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", eventType.name().toLowerCase());
        envelope.put("emittedAt", Instant.now().toString());
        envelope.put("payload", payload);
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Ops 事件。", exception);
        }
    }

    public Flux<String> stream() {
        return sink.asFlux();
    }
}
