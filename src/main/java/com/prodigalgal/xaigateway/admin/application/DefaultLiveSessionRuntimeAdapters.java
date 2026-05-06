package com.prodigalgal.xaigateway.admin.application;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

abstract class SimulatedLiveSessionRuntimeAdapter implements LiveSessionRuntimeAdapter {

    private final String protocol;
    private final String adapterName;
    private final String transport;

    SimulatedLiveSessionRuntimeAdapter(String protocol, String adapterName) {
        this(protocol, adapterName, "simulated");
    }

    SimulatedLiveSessionRuntimeAdapter(String protocol, String adapterName, String transport) {
        this.protocol = protocol;
        this.adapterName = adapterName;
        this.transport = transport;
    }

    @Override
    public String protocol() {
        return protocol;
    }

    @Override
    public String transport() {
        return transport;
    }

    @Override
    public LiveSessionRuntimeConnectResult connect(LiveSessionRuntimeRequest request) {
        String upstreamResumeHandle = "upstream_" + request.sessionKey();
        return new LiveSessionRuntimeConnectResult(
                adapterName,
                upstreamResumeHandle,
                Map.of(
                        "adapter", adapterName,
                        "transport", transport,
                        "runtimeState", "CONNECTED",
                        "upstreamResumeHandle", upstreamResumeHandle
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "runtime.connected",
                        "{\"adapter\":\"" + adapterName + "\",\"upstreamResumeHandle\":\"" + upstreamResumeHandle + "\"}",
                        0L
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult send(LiveSessionRuntimeRequest request, LiveSessionRuntimeMessage message) {
        return new LiveSessionRuntimeExchangeResult(
                adapterName,
                Map.of(
                        "adapter", adapterName,
                        "transport", transport,
                        "runtimeState", "STREAMING",
                        "lastClientEventType", message.eventType()
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "provider." + message.eventType(),
                        "{\"adapter\":\"" + adapterName + "\",\"receivedEventType\":\"" + message.eventType() + "\"}",
                        message.audioBytes()
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult heartbeat(LiveSessionRuntimeRequest request) {
        return new LiveSessionRuntimeExchangeResult(
                adapterName,
                Map.of(
                        "adapter", adapterName,
                        "transport", transport,
                        "runtimeState", "CONNECTED",
                        "heartbeat", "ok"
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "runtime.heartbeat",
                        "{\"adapter\":\"" + adapterName + "\",\"status\":\"ok\"}",
                        0L
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult close(LiveSessionRuntimeRequest request) {
        return new LiveSessionRuntimeExchangeResult(
                adapterName,
                Map.of(
                        "adapter", adapterName,
                        "transport", transport,
                        "runtimeState", "CLOSED"
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "runtime.closed",
                        "{\"adapter\":\"" + adapterName + "\"}",
                        0L
                ))
        );
    }
}

abstract class ProviderWebSocketLiveSessionRuntimeAdapter implements LiveSessionRuntimeAdapter {

    private final String protocol;
    private final String adapterName;
    private final String provider;
    private final String upstreamWebSocketUrl;
    private final String authScheme;

    ProviderWebSocketLiveSessionRuntimeAdapter(
            String protocol,
            String adapterName,
            String provider,
            String upstreamWebSocketUrl,
            String authScheme) {
        this.protocol = protocol;
        this.adapterName = adapterName;
        this.provider = provider;
        this.upstreamWebSocketUrl = upstreamWebSocketUrl;
        this.authScheme = authScheme;
    }

    @Override
    public String protocol() {
        return protocol;
    }

    @Override
    public String transport() {
        return "websocket";
    }

    @Override
    public LiveSessionRuntimeConnectResult connect(LiveSessionRuntimeRequest request) {
        String upstreamResumeHandle = provider + "_" + request.sessionKey();
        Map<String, String> metadata = baseMetadata("CONNECTED", "OPEN");
        metadata.put("upstreamWebSocketUrl", upstreamWebSocketUrl);
        metadata.put("authScheme", authScheme);
        metadata.put("upstreamResumeHandle", upstreamResumeHandle);
        metadata.put("latencyMs", "0");
        return new LiveSessionRuntimeConnectResult(
                adapterName,
                upstreamResumeHandle,
                metadata,
                List.of(new LiveSessionRuntimeProviderEvent(
                        "websocket.connected",
                        "{\"provider\":\"" + provider + "\",\"transport\":\"websocket\",\"model\":\"" + escapeJson(request.model()) + "\",\"upstreamWebSocketUrl\":\"" + upstreamWebSocketUrl + "\",\"binaryFramePolicy\":\"json_control_plus_binary_audio_frames\"}",
                        0L
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult send(LiveSessionRuntimeRequest request, LiveSessionRuntimeMessage message) {
        String normalizedErrorCode = normalizedProviderErrorCode(message);
        String eventType = providerRuntimeEventType(message, normalizedErrorCode);
        Map<String, String> metadata = baseMetadata("STREAMING", "OPEN");
        metadata.put("lastClientEventType", message.eventType());
        metadata.put("lastProviderEventType", providerEventType(message.eventType()));
        metadata.put("inputAudioBytes", String.valueOf(message.audioBytes()));
        metadata.put("latencyMs", "0");
        metadata.put("usageInputAudioBytes", String.valueOf(message.audioBytes()));
        metadata.put("usageOutputAudioBytes", String.valueOf(message.audioBytes()));
        if (normalizedErrorCode != null) {
            metadata.put("normalizedProviderErrorCode", normalizedErrorCode);
            metadata.put("retryable", String.valueOf(isRetryable(normalizedErrorCode)));
        }
        if (isRetryEvent(message.eventType())) {
            metadata.put("retryable", "true");
            metadata.put("retryAfterMs", "1000");
        }
        return new LiveSessionRuntimeExchangeResult(
                adapterName,
                metadata,
                List.of(new LiveSessionRuntimeProviderEvent(
                        eventType,
                        providerEventPayload(message, normalizedErrorCode),
                        message.audioBytes()
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult heartbeat(LiveSessionRuntimeRequest request) {
        Map<String, String> metadata = baseMetadata("CONNECTED", "OPEN");
        metadata.put("heartbeat", "pong");
        metadata.put("latencyMs", "0");
        return new LiveSessionRuntimeExchangeResult(
                adapterName,
                metadata,
                List.of(new LiveSessionRuntimeProviderEvent(
                        "websocket.pong",
                        "{\"provider\":\"" + provider + "\",\"status\":\"pong\"}",
                        0L
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult close(LiveSessionRuntimeRequest request) {
        Map<String, String> metadata = baseMetadata("CLOSED", "CLOSED");
        metadata.put("closeReason", "client_closed");
        metadata.put("cancelSemantic", "gateway_close_as_client_cancel");
        metadata.put("latencyMs", "0");
        return new LiveSessionRuntimeExchangeResult(
                adapterName,
                metadata,
                List.of(new LiveSessionRuntimeProviderEvent(
                        "websocket.closed",
                        "{\"provider\":\"" + provider + "\",\"status\":\"closed\",\"reason\":\"client_closed\"}",
                        0L
                ))
        );
    }

    private String providerEventType(String gatewayEventType) {
        if (gatewayEventType == null || gatewayEventType.isBlank()) {
            return provider + ".input";
        }
        if (gatewayEventType.startsWith("audio.")) {
            return provider + ".audio." + gatewayEventType.substring("audio.".length());
        }
        return provider + "." + gatewayEventType;
    }

    private Map<String, String> baseMetadata(String runtimeState, String websocketState) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("adapter", adapterName);
        metadata.put("transport", "websocket");
        metadata.put("provider", provider);
        metadata.put("runtimeState", runtimeState);
        metadata.put("websocketState", websocketState);
        metadata.put("binaryFramePolicy", "json_control_plus_binary_audio_frames");
        metadata.put("errorSchema", "gateway_realtime_error_v1");
        metadata.put("retryPolicy", "resume_token_with_backoff");
        metadata.put("closePolicy", "client_close_or_provider_close");
        return metadata;
    }

    private String providerRuntimeEventType(LiveSessionRuntimeMessage message, String normalizedErrorCode) {
        if (normalizedErrorCode != null) {
            return "websocket.error";
        }
        if (isRetryEvent(message.eventType())) {
            return "websocket.retry";
        }
        return "websocket.frame." + message.eventType();
    }

    private String providerEventPayload(LiveSessionRuntimeMessage message, String normalizedErrorCode) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"provider\":\"").append(provider).append("\",")
                .append("\"providerEventType\":\"").append(providerEventType(message.eventType())).append("\",")
                .append("\"binaryFramePolicy\":\"json_control_plus_binary_audio_frames\",")
                .append("\"audioBytes\":").append(message.audioBytes());
        if (normalizedErrorCode != null) {
            builder.append(",\"normalizedProviderErrorCode\":\"").append(normalizedErrorCode).append("\"")
                    .append(",\"retryable\":").append(isRetryable(normalizedErrorCode));
        }
        if (isRetryEvent(message.eventType())) {
            builder.append(",\"retryAfterMs\":1000");
        }
        builder.append("}");
        return builder.toString();
    }

    private String normalizedProviderErrorCode(LiveSessionRuntimeMessage message) {
        String eventType = message.eventType() == null ? "" : message.eventType().toLowerCase(Locale.ROOT);
        String payload = message.payloadJson() == null ? "" : message.payloadJson().toLowerCase(Locale.ROOT);
        if (eventType.contains("timeout") || payload.contains("timeout")) {
            return "UPSTREAM_TIMEOUT";
        }
        if (payload.contains("rate_limit") || payload.contains("rate limit")) {
            return "UPSTREAM_RATE_LIMIT";
        }
        if (payload.contains("server_error") || payload.contains("internal")) {
            return "UPSTREAM_SERVER_ERROR";
        }
        if (eventType.startsWith("error") || payload.contains("\"error\"")) {
            return "UPSTREAM_PROVIDER_ERROR";
        }
        return null;
    }

    private boolean isRetryEvent(String eventType) {
        return eventType != null && eventType.toLowerCase(Locale.ROOT).contains("retry");
    }

    private boolean isRetryable(String normalizedErrorCode) {
        return "UPSTREAM_TIMEOUT".equals(normalizedErrorCode)
                || "UPSTREAM_RATE_LIMIT".equals(normalizedErrorCode)
                || "UPSTREAM_SERVER_ERROR".equals(normalizedErrorCode);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

@Component
class OpenAiRealtimeRuntimeAdapter extends ProviderWebSocketLiveSessionRuntimeAdapter {
    OpenAiRealtimeRuntimeAdapter() {
        super(
                "openai_realtime",
                "openai-realtime-runtime",
                "openai_realtime",
                "wss://api.openai.com/v1/realtime",
                "Authorization: Bearer <api-key>"
        );
    }
}

@Component
class GeminiLiveRuntimeAdapter extends ProviderWebSocketLiveSessionRuntimeAdapter {
    GeminiLiveRuntimeAdapter() {
        super(
                "gemini_live",
                "gemini-live-runtime",
                "gemini_live",
                "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
                "x-goog-api-key: <api-key>"
        );
    }
}

@Component
class MockRealtimeRuntimeAdapter extends SimulatedLiveSessionRuntimeAdapter {
    MockRealtimeRuntimeAdapter() {
        super("mock_realtime", "mock-realtime-runtime");
    }
}

@Component
class MockWebSocketRealtimeRuntimeAdapter extends SimulatedLiveSessionRuntimeAdapter {
    MockWebSocketRealtimeRuntimeAdapter() {
        super("mock_websocket_realtime", "mock-websocket-realtime-runtime", "websocket");
    }

    @Override
    public LiveSessionRuntimeConnectResult connect(LiveSessionRuntimeRequest request) {
        String upstreamResumeHandle = "ws_" + request.sessionKey();
        return new LiveSessionRuntimeConnectResult(
                "mock-websocket-realtime-runtime",
                upstreamResumeHandle,
                Map.of(
                        "adapter", "mock-websocket-realtime-runtime",
                        "transport", "websocket",
                        "runtimeState", "CONNECTED",
                        "websocketState", "OPEN",
                        "upstreamResumeHandle", upstreamResumeHandle
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "websocket.connected",
                        "{\"transport\":\"websocket\",\"adapter\":\"mock-websocket-realtime-runtime\",\"upstreamResumeHandle\":\"" + upstreamResumeHandle + "\"}",
                        0L
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult send(LiveSessionRuntimeRequest request, LiveSessionRuntimeMessage message) {
        return new LiveSessionRuntimeExchangeResult(
                "mock-websocket-realtime-runtime",
                Map.of(
                        "adapter", "mock-websocket-realtime-runtime",
                        "transport", "websocket",
                        "runtimeState", "STREAMING",
                        "websocketState", "OPEN",
                        "lastClientEventType", message.eventType()
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "websocket.frame." + message.eventType(),
                        "{\"transport\":\"websocket\",\"receivedEventType\":\"" + message.eventType() + "\",\"audioBytes\":" + message.audioBytes() + "}",
                        message.audioBytes()
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult heartbeat(LiveSessionRuntimeRequest request) {
        return new LiveSessionRuntimeExchangeResult(
                "mock-websocket-realtime-runtime",
                Map.of(
                        "adapter", "mock-websocket-realtime-runtime",
                        "transport", "websocket",
                        "runtimeState", "CONNECTED",
                        "websocketState", "OPEN",
                        "heartbeat", "pong"
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "websocket.pong",
                        "{\"transport\":\"websocket\",\"status\":\"pong\"}",
                        0L
                ))
        );
    }

    @Override
    public LiveSessionRuntimeExchangeResult close(LiveSessionRuntimeRequest request) {
        return new LiveSessionRuntimeExchangeResult(
                "mock-websocket-realtime-runtime",
                Map.of(
                        "adapter", "mock-websocket-realtime-runtime",
                        "transport", "websocket",
                        "runtimeState", "CLOSED",
                        "websocketState", "CLOSED"
                ),
                List.of(new LiveSessionRuntimeProviderEvent(
                        "websocket.closed",
                        "{\"transport\":\"websocket\",\"status\":\"closed\"}",
                        0L
                ))
        );
    }
}
