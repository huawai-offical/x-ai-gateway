package com.prodigalgal.xaigateway.admin.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

abstract class SimulatedLiveSessionRuntimeAdapter implements LiveSessionRuntimeAdapter {

    private final String protocol;
    private final String adapterName;

    SimulatedLiveSessionRuntimeAdapter(String protocol, String adapterName) {
        this.protocol = protocol;
        this.adapterName = adapterName;
    }

    @Override
    public String protocol() {
        return protocol;
    }

    @Override
    public LiveSessionRuntimeConnectResult connect(LiveSessionRuntimeRequest request) {
        String upstreamResumeHandle = "upstream_" + request.sessionKey();
        return new LiveSessionRuntimeConnectResult(
                adapterName,
                upstreamResumeHandle,
                Map.of(
                        "adapter", adapterName,
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

@Component
class OpenAiRealtimeRuntimeAdapter extends SimulatedLiveSessionRuntimeAdapter {
    OpenAiRealtimeRuntimeAdapter() {
        super("openai_realtime", "openai-realtime-runtime");
    }
}

@Component
class GeminiLiveRuntimeAdapter extends SimulatedLiveSessionRuntimeAdapter {
    GeminiLiveRuntimeAdapter() {
        super("gemini_live", "gemini-live-runtime");
    }
}
