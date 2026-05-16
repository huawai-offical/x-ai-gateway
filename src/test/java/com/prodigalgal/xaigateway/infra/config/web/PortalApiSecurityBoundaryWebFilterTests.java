package com.prodigalgal.xaigateway.infra.config.web;

import com.prodigalgal.xaigateway.infra.persistence.entity.AuditLogEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.AuditLogRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalApiSecurityBoundaryWebFilterTests {

    @Test
    void shouldDenyProtectedPortalApiWithoutPortalSessionAndAudit() {
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        PortalApiSecurityBoundaryWebFilter filter = new PortalApiSecurityBoundaryWebFilter(new ObjectMapper(), auditLogRepository);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal/keys").build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, chain(chainInvoked)).block();

        assertFalse(chainInvoked.get());
        assertTrue(exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED);
        Mockito.verify(auditLogRepository).save(Mockito.argThat(entity ->
                entity instanceof AuditLogEntity
                        && "PORTAL_API_BOUNDARY".equals(entity.getAuditType())
                        && "/portal/keys".equals(entity.getPath())
                        && "FAILED".equals(entity.getStatus())
        ));
    }

    @Test
    void shouldAllowProtectedPortalApiWithPortalSession() {
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        PortalApiSecurityBoundaryWebFilter filter = new PortalApiSecurityBoundaryWebFilter(new ObjectMapper(), auditLogRepository);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal/keys").build());
        exchange.getSession().block().getAttributes().put("portalUserId", 42L);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, chain(chainInvoked)).block();

        assertTrue(chainInvoked.get());
        Mockito.verifyNoInteractions(auditLogRepository);
    }

    @Test
    void shouldAllowPublicPortalAuthEndpointsWithoutSession() {
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        PortalApiSecurityBoundaryWebFilter filter = new PortalApiSecurityBoundaryWebFilter(new ObjectMapper(), auditLogRepository);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/portal/auth/login").build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, chain(chainInvoked)).block();

        assertTrue(chainInvoked.get());
        Mockito.verifyNoInteractions(auditLogRepository);
    }

    private WebFilterChain chain(AtomicBoolean chainInvoked) {
        return exchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };
    }
}
