package com.prodigalgal.xaigateway.infra.config.web;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class TraceIdWebFilter implements WebFilter {

    public static final String TRACE_ID_ATTRIBUTE = "gatewayTraceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveTraceId(exchange);
        exchange.getAttributes().put(TRACE_ID_ATTRIBUTE, traceId);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return chain.filter(exchange);
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }

        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId != null && !requestId.isBlank()) {
            return requestId.trim();
        }

        return UUID.randomUUID().toString();
    }
}
