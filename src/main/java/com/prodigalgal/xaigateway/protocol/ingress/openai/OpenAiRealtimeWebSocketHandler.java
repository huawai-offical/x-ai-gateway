package com.prodigalgal.xaigateway.protocol.ingress.openai;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
public class OpenAiRealtimeWebSocketHandler implements WebSocketHandler {

    private final OpenAiRealtimeWebSocketBridge bridge;

    public OpenAiRealtimeWebSocketHandler(OpenAiRealtimeWebSocketBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        OpenAiRealtimeWebSocketContext context;
        try {
            context = bridge.open(session.getHandshakeInfo().getHeaders(), session.getHandshakeInfo().getUri());
        } catch (RuntimeException exception) {
            return session.send(Mono.just(session.textMessage(bridge.errorEvent("Realtime authentication failed.", "authorization", null))));
        }

        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        outbound.tryEmitNext(bridge.sessionCreated(context));

        Mono<Void> receive = session.receive()
                .flatMap(message -> handleMessage(context, message, outbound))
                .doFinally(signalType -> {
                    bridge.close(context);
                    outbound.tryEmitComplete();
                })
                .then();

        Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
        return send.and(receive);
    }

    private Mono<Void> handleMessage(
            OpenAiRealtimeWebSocketContext context,
            WebSocketMessage message,
            Sinks.Many<String> outbound) {
        List<String> responses;
        if (message.getType() != WebSocketMessage.Type.TEXT) {
            responses = List.of(bridge.unsupportedFrameError());
        } else {
            responses = bridge.acceptText(context, message.getPayloadAsText());
        }
        responses.forEach(outbound::tryEmitNext);
        return Mono.empty();
    }
}
