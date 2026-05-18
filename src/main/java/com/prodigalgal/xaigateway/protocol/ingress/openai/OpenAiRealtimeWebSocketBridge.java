package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.admin.api.LiveSessionCreateRequest;
import com.prodigalgal.xaigateway.admin.api.LiveSessionResponse;
import com.prodigalgal.xaigateway.admin.api.LiveSessionRuntimeEventRequest;
import com.prodigalgal.xaigateway.admin.application.LiveSessionService;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayTokenAuthenticationResolver;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OpenAiRealtimeWebSocketBridge {

    private static final String DEFAULT_MODEL = "gpt-realtime";
    private static final String PROTOCOL = "openai_realtime";
    private static final long DEFAULT_TTL_SECONDS = 1_800L;

    private final GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver;
    private final LiveSessionService liveSessionService;
    private final ObjectMapper objectMapper;

    public OpenAiRealtimeWebSocketBridge(
            GatewayTokenAuthenticationResolver gatewayTokenAuthenticationResolver,
            LiveSessionService liveSessionService,
            ObjectMapper objectMapper) {
        this.gatewayTokenAuthenticationResolver = gatewayTokenAuthenticationResolver;
        this.liveSessionService = liveSessionService;
        this.objectMapper = objectMapper;
    }

    public OpenAiRealtimeWebSocketContext open(HttpHeaders headers, URI uri) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalArgumentException("Missing Authorization header.");
        }
        AuthenticatedDistributedKey distributedKey = gatewayTokenAuthenticationResolver.authenticate(
                authorization,
                null,
                null,
                null
        );
        String model = queryParam(uri, "model", DEFAULT_MODEL);
        LiveSessionResponse created = liveSessionService.create(new LiveSessionCreateRequest(
                distributedKey.id(),
                model,
                PROTOCOL,
                metadataJson(model),
                DEFAULT_TTL_SECONDS
        ));
        LiveSessionResponse connected = liveSessionService.connect(created.sessionKey());
        return new OpenAiRealtimeWebSocketContext(
                connected.sessionKey(),
                distributedKey.id(),
                model,
                new AtomicLong(0L)
        );
    }

    public String sessionCreated(OpenAiRealtimeWebSocketContext context) {
        return writeJson(Map.of(
                "type", "session.created",
                "event_id", context.nextEventId(),
                "session", sessionObject(context, "created")
        ));
    }

    public List<String> acceptText(OpenAiRealtimeWebSocketContext context, String text) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(text == null || text.isBlank() ? "{}" : text);
        } catch (Exception exception) {
            return List.of(errorEvent("Invalid JSON event payload.", null, null));
        }
        String eventType = textField(payload, "type");
        if (eventType == null || eventType.isBlank()) {
            return List.of(errorEvent("The 'type' field is missing.", "type", textField(payload, "event_id")));
        }
        long audioBytes = audioBytes(payload);
        liveSessionService.sendRuntimeEvent(context.sessionKey(), new LiveSessionRuntimeEventRequest(
                eventType,
                writeJson(payload),
                audioBytes
        ));
        if ("session.update".equals(eventType)) {
            return List.of(sessionUpdated(context, payload));
        }
        if ("input_audio_buffer.commit".equals(eventType)) {
            return List.of(inputAudioBufferCommitted(context));
        }
        return List.of();
    }

    public String unsupportedFrameError() {
        return errorEvent("Only JSON text WebSocket frames are supported by this Realtime gateway.", "frame", null);
    }

    public String errorEvent(String message, String param, String eventId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "invalid_request_error");
        error.put("message", message);
        error.put("param", param);
        error.put("event_id", eventId);
        return writeJson(Map.of(
                "type", "error",
                "event_id", "event_error",
                "error", error
        ));
    }

    public void close(OpenAiRealtimeWebSocketContext context) {
        if (context != null && context.sessionKey() != null) {
            liveSessionService.close(context.sessionKey());
        }
    }

    private String sessionUpdated(OpenAiRealtimeWebSocketContext context, JsonNode payload) {
        Map<String, Object> session = sessionObject(context, "updated");
        JsonNode requestedSession = payload.path("session");
        if (requestedSession != null && requestedSession.isObject()) {
            session.put("client_update", requestedSession);
        }
        return writeJson(Map.of(
                "type", "session.updated",
                "event_id", context.nextEventId(),
                "session", session
        ));
    }

    private String inputAudioBufferCommitted(OpenAiRealtimeWebSocketContext context) {
        String itemId = "item_" + context.eventSequence().get();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "input_audio_buffer.committed");
        event.put("event_id", context.nextEventId());
        event.put("previous_item_id", null);
        event.put("item_id", itemId);
        return writeJson(event);
    }

    private Map<String, Object> sessionObject(OpenAiRealtimeWebSocketContext context, String status) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", context.sessionKey());
        session.put("object", "realtime.session");
        session.put("type", "realtime");
        session.put("model", context.model());
        session.put("status", status);
        session.put("transport", "websocket");
        return session;
    }

    private String metadataJson(String model) {
        return writeJson(Map.of(
                "ingress", "openai_realtime_websocket",
                "officialEndpoint", "/v1/realtime",
                "model", model
        ));
    }

    private String queryParam(URI uri, String name, String fallback) {
        if (uri == null) {
            return fallback;
        }
        String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String textField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private long audioBytes(JsonNode payload) {
        List<String> candidates = new ArrayList<>();
        collectText(payload, "audio", candidates);
        collectText(payload, "delta", candidates);
        for (String candidate : candidates) {
            try {
                return Base64.getDecoder().decode(candidate).length;
            } catch (IllegalArgumentException ignored) {
                // 非 base64 字段不计入音频字节。
            }
        }
        return 0L;
    }

    private void collectText(JsonNode node, String field, List<String> candidates) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        JsonNode direct = node.path(field);
        if (direct != null && direct.isTextual() && !direct.asText().isBlank()) {
            candidates.add(direct.asText());
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> collectText(entry.getValue(), field, candidates));
        } else if (node.isArray()) {
            node.forEach(child -> collectText(child, field, candidates));
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"type\":\"error\",\"error\":{\"type\":\"server_error\",\"message\":\"Failed to serialize realtime event.\"}}";
        }
    }
}
