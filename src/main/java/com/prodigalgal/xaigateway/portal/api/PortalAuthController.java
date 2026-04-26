package com.prodigalgal.xaigateway.portal.api;

import com.prodigalgal.xaigateway.portal.application.PortalAuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Validated
@RestController
@RequestMapping("/portal")
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    public PortalAuthController(PortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
    }

    @GetMapping("/auth/session")
    public Mono<PortalSessionResponse> session(ServerWebExchange exchange) {
        return portalAuthService.currentSession(exchange);
    }

    @PostMapping("/auth/register")
    public Mono<PortalSessionResponse> register(
            @Valid @RequestBody PortalRegisterRequest request,
            ServerWebExchange exchange) {
        return portalAuthService.register(request, exchange);
    }

    @PostMapping("/auth/login")
    public Mono<PortalSessionResponse> login(
            @Valid @RequestBody PortalLoginRequest request,
            ServerWebExchange exchange) {
        return portalAuthService.login(request, exchange);
    }

    @PostMapping("/auth/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        return portalAuthService.logout(exchange);
    }

    @GetMapping("/subscriptions")
    public Mono<List<PortalSubscriptionResponse>> subscriptions(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::listSubscriptions);
    }

    @GetMapping("/keys")
    public Mono<List<PortalKeyResponse>> keys(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::listKeys);
    }

    @PostMapping("/keys")
    public Mono<PortalKeyCreateResponse> createKey(
            @Valid @RequestBody PortalKeyCreateRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalAuthService.createKey(session, request));
    }

    @PostMapping("/keys/{id}/rotate")
    public Mono<PortalKeyCreateResponse> rotateKey(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalAuthService.rotateKey(session, id));
    }

    @PostMapping("/keys/{id}/disable")
    public Mono<PortalKeyResponse> disableKey(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalAuthService.disableKey(session, id));
    }

    @GetMapping("/announcements")
    public Mono<List<PortalAnnouncementResponse>> announcements(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::listAnnouncements);
    }

    @PostMapping("/announcements/{id}/read")
    public Mono<PortalAnnouncementResponse> markAnnouncementRead(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalAuthService.markAnnouncementRead(session, id));
    }

    @GetMapping("/redeem/status")
    public Mono<PortalRedeemStatusResponse> redeemStatus(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::redeemStatus);
    }

    @PostMapping("/redeem")
    public Mono<PortalRedeemResponse> redeem(
            @Valid @RequestBody PortalRedeemRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalAuthService.redeem(session, request.code()));
    }

    @GetMapping("/balance-ledger")
    public Mono<List<PortalBalanceLedgerResponse>> balanceLedger(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::listBalanceLedger);
    }
}
