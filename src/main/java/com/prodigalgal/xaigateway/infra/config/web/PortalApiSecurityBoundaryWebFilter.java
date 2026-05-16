package com.prodigalgal.xaigateway.infra.config.web;

import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PortalApiSecurityBoundaryWebFilter implements WebFilter {

    private static final String PORTAL_USER_ID_SESSION_KEY = "portalUserId";

    private final ObjectMapper objectMapper;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public PortalApiSecurityBoundaryWebFilter(
            ObjectMapper objectMapper,
            ObjectProvider<AuditLogRepository> auditLogRepositoryProvider) {
        this(objectMapper, auditLogRepositoryProvider.getIfAvailable());
    }

    PortalApiSecurityBoundaryWebFilter(ObjectMapper objectMapper, AuditLogRepository auditLogRepository) {
        this.objectMapper = objectMapper;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/portal") || exchange.getRequest().getMethod() == HttpMethod.OPTIONS || isPublicPortalEndpoint(path)) {
            return chain.filter(exchange);
        }

        return exchange.getSession().flatMap(session -> {
            if (session.getAttributes().containsKey(PORTAL_USER_ID_SESSION_KEY)) {
                return chain.filter(exchange);
            }
            auditDenied(path);
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "PORTAL_UNAUTHORIZED", "请先登录用户门户。");
        });
    }

    private boolean isPublicPortalEndpoint(String path) {
        return "/portal/auth/session".equals(path)
                || "/portal/auth/register".equals(path)
                || "/portal/auth/login".equals(path)
                || "/portal/auth/logout".equals(path)
                || "/portal/auth/captcha/challenge".equals(path)
                || "/portal/auth/passkeys/assertion/start".equals(path)
                || "/portal/auth/passkeys/assertion/finish".equals(path)
                || "/portal/auth/oauth/providers".equals(path)
                || isPublicPortalOAuthEndpoint(path);
    }

    private boolean isPublicPortalOAuthEndpoint(String path) {
        return path.startsWith("/portal/auth/oauth/")
                && (path.endsWith("/start") || path.endsWith("/callback"));
    }

    private void auditDenied(String path) {
        if (auditLogRepository == null) {
            return;
        }
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAuditType("PORTAL_API_BOUNDARY");
        entity.setAction("PORTAL_API_DENIED");
        entity.setTargetType("portal_session");
        entity.setTargetId("anonymous");
        entity.setStatus("FAILED");
        entity.setActor("anonymous");
        entity.setPath(path);
        entity.setDetailJson("{\"reason\":\"missing_portal_session\"}");
        auditLogRepository.save(entity);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Object traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTRIBUTE);
        ApiErrorResponse payload = new ApiErrorResponse(code, message, traceId == null ? null : traceId.toString());
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            byte[] fallback = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(fallback)));
        }
    }
}
