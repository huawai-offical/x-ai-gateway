package com.prodigalgal.xaigateway.infra.config;

import com.prodigalgal.xaigateway.gateway.core.observability.GatewayAuditLogService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AdminAuditWebFilter implements WebFilter {

    private final GatewayAuditLogService gatewayAuditLogService;

    public AdminAuditWebFilter(ObjectProvider<GatewayAuditLogService> gatewayAuditLogService) {
        this.gatewayAuditLogService = gatewayAuditLogService.getIfAvailable();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!shouldAudit(exchange)) {
            return chain.filter(exchange);
        }

        Instant startedAt = Instant.now();
        return chain.filter(exchange)
                .doOnSuccess(ignored -> record(exchange, startedAt, null))
                .doOnError(error -> record(exchange, startedAt, error));
    }

    private boolean shouldAudit(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        return path.startsWith("/admin/")
                && method != null
                && method != HttpMethod.GET;
    }

    private void record(ServerWebExchange exchange, Instant startedAt, Throwable error) {
        if (gatewayAuditLogService == null) {
            return;
        }
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode == null ? (error == null ? 200 : 500) : statusCode.value();
        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("method", exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name());
        detail.put("query", exchange.getRequest().getQueryParams().toSingleValueMap());
        detail.put("statusCode", status);
        detail.put("durationMs", durationMs);
        if (error != null) {
            detail.put("errorType", error.getClass().getSimpleName());
            detail.put("errorMessage", error.getMessage());
        }
        gatewayAuditLogService.recordAdminApiAction(
                exchange.getRequest().getId(),
                exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value(),
                error == null ? "SUCCESS" : "FAILED",
                "system",
                detail
        );
    }
}
