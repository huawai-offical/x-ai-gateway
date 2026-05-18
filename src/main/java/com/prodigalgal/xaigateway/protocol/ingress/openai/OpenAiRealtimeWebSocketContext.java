package com.prodigalgal.xaigateway.protocol.ingress.openai;

import java.util.concurrent.atomic.AtomicLong;

record OpenAiRealtimeWebSocketContext(
        String sessionKey,
        Long distributedKeyId,
        String model,
        AtomicLong eventSequence
) {

    String nextEventId() {
        return "event_" + eventSequence.incrementAndGet();
    }
}
