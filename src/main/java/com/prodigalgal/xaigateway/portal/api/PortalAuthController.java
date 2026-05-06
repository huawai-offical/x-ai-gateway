package com.prodigalgal.xaigateway.portal.api;

import com.prodigalgal.xaigateway.admin.api.PaymentOrderResponse;
import com.prodigalgal.xaigateway.admin.application.PaymentAdminService;
import com.prodigalgal.xaigateway.portal.application.PortalAuthService;
import com.prodigalgal.xaigateway.portal.application.PortalSecurityService;
import com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final PortalSocialOAuthService portalSocialOAuthService;
    private final PortalSecurityService portalSecurityService;
    private final PaymentAdminService paymentAdminService;

    public PortalAuthController(
            PortalAuthService portalAuthService,
            PortalSocialOAuthService portalSocialOAuthService,
            PortalSecurityService portalSecurityService,
            PaymentAdminService paymentAdminService) {
        this.portalAuthService = portalAuthService;
        this.portalSocialOAuthService = portalSocialOAuthService;
        this.portalSecurityService = portalSecurityService;
        this.paymentAdminService = paymentAdminService;
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

    @PostMapping("/auth/captcha/challenge")
    public PortalCaptchaChallengeResponse captchaChallenge() {
        return portalSecurityService.createCaptchaChallenge();
    }

    @GetMapping("/auth/security/status")
    public Mono<PortalSecurityStatusResponse> securityStatus(ServerWebExchange exchange) {
        return exchange.getSession().map(portalSecurityService::status);
    }

    @GetMapping("/auth/security/passkeys")
    public Mono<List<PortalPasskeyCredentialResponse>> passkeys(ServerWebExchange exchange) {
        return exchange.getSession().map(portalSecurityService::listPasskeys);
    }

    @PostMapping("/auth/security/passkeys/registration/start")
    public Mono<PortalPasskeyRegistrationStartResponse> startPasskeyRegistration(
            @RequestBody(required = false) PortalPasskeyRegistrationStartRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalSecurityService.startPasskeyRegistration(session, request));
    }

    @PostMapping("/auth/security/passkeys/registration/finish")
    public Mono<PortalPasskeyCredentialResponse> finishPasskeyRegistration(
            @RequestBody PortalPasskeyRegistrationFinishRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalSecurityService.finishPasskeyRegistration(session, request));
    }

    @PostMapping("/auth/passkeys/assertion/start")
    public PortalPasskeyAssertionStartResponse startPasskeyAssertion(@RequestBody PortalPasskeyAssertionStartRequest request) {
        return portalSecurityService.startPasskeyAssertion(request);
    }

    @PostMapping("/auth/passkeys/assertion/finish")
    public Mono<PortalSessionResponse> finishPasskeyAssertion(
            @RequestBody PortalPasskeyAssertionFinishRequest request,
            ServerWebExchange exchange) {
        return portalAuthService.authenticateExternalUser(portalSecurityService.finishPasskeyAssertion(request), exchange);
    }

    @DeleteMapping("/auth/security/passkeys/{id}")
    public Mono<List<PortalPasskeyCredentialResponse>> deletePasskey(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalSecurityService.deletePasskey(session, id));
    }

    @PostMapping("/auth/email-verification/start")
    public Mono<PortalEmailVerificationStartResponse> startEmailVerification(ServerWebExchange exchange) {
        return exchange.getSession().map(portalSecurityService::startEmailVerification);
    }

    @PostMapping("/auth/email-verification/confirm")
    public Mono<PortalSecurityStatusResponse> confirmEmailVerification(
            @Valid @RequestBody PortalEmailVerificationConfirmRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalSecurityService.confirmEmailVerification(session, request));
    }

    @PostMapping("/auth/totp/setup")
    public Mono<PortalTotpSetupResponse> setupTotp(ServerWebExchange exchange) {
        return exchange.getSession().map(portalSecurityService::setupTotp);
    }

    @PostMapping("/auth/totp/enable")
    public Mono<PortalSecurityStatusResponse> enableTotp(
            @Valid @RequestBody PortalTotpVerifyRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalSecurityService.enableTotp(session, request));
    }

    @PostMapping("/auth/totp/disable")
    public Mono<PortalSecurityStatusResponse> disableTotp(
            @Valid @RequestBody PortalTotpVerifyRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalSecurityService.disableTotp(session, request));
    }

    @GetMapping("/auth/oauth/providers")
    public List<PortalSocialOAuthProviderResponse> oauthProviders() {
        return portalSocialOAuthService.providers();
    }

    @PostMapping("/auth/oauth/{provider}/start")
    public PortalSocialOAuthStartResponse oauthStart(
            @PathVariable String provider,
            @RequestBody(required = false) PortalSocialOAuthStartRequest request) {
        return portalSocialOAuthService.start(provider, request);
    }

    @PostMapping("/auth/oauth/{provider}/callback")
    public Mono<PortalSessionResponse> oauthCallback(
            @PathVariable String provider,
            @Valid @RequestBody PortalSocialOAuthCallbackRequest request,
            ServerWebExchange exchange) {
        return portalSocialOAuthService.complete(provider, request, exchange);
    }

    @GetMapping("/auth/oauth/identities")
    public Mono<List<PortalSocialOAuthIdentityResponse>> oauthIdentities(ServerWebExchange exchange) {
        return exchange.getSession().map(portalSocialOAuthService::identities);
    }

    @DeleteMapping("/auth/oauth/{provider}/identities")
    public Mono<List<PortalSocialOAuthIdentityResponse>> oauthUnlink(
            @PathVariable String provider,
            @RequestBody PortalSocialOAuthUnlinkRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> portalSocialOAuthService.unlink(session, provider, request));
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

    @GetMapping("/profile")
    public Mono<PortalProfileResponse> profile(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::profile);
    }

    @GetMapping("/self-service/summary")
    public Mono<PortalSelfServiceSummaryResponse> selfServiceSummary(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::selfServiceSummary);
    }

    @GetMapping("/orders")
    public Mono<List<PortalPaymentOrderResponse>> paymentOrders(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::listPaymentOrders);
    }

    @PostMapping("/orders")
    public Mono<PortalPaymentOrderResponse> createPaymentOrder(
            @Valid @RequestBody PortalPaymentOrderCreateRequest request,
            ServerWebExchange exchange) {
        return exchange.getSession().map(session -> {
            var user = portalAuthService.requireCurrentPortalUser(session);
            PaymentOrderResponse order = paymentAdminService.createForUser(
                    user,
                    request.provider(),
                    request.amountMinor(),
                    request.currency(),
                    request.tokenCredits(),
                    request.metadataJson()
            );
            return toPortalPaymentOrderResponse(order);
        });
    }

    @GetMapping("/usage/summary")
    public Mono<PortalUsageSummaryResponse> usageSummary(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::usageSummary);
    }

    @GetMapping("/channels/status")
    public Mono<List<PortalChannelStatusResponse>> channelStatuses(ServerWebExchange exchange) {
        return exchange.getSession().map(portalAuthService::channelStatuses);
    }

    private PortalPaymentOrderResponse toPortalPaymentOrderResponse(PaymentOrderResponse order) {
        return new PortalPaymentOrderResponse(
                order.id(),
                order.orderNo(),
                order.provider(),
                order.amountMinor(),
                order.currency(),
                order.tokenCredits(),
                order.status(),
                order.providerTradeNo(),
                order.providerInstanceCode(),
                order.checkoutUrl(),
                order.checkoutMethod(),
                order.checkoutExpiresAt(),
                order.refundAmountMinor(),
                order.refundedAt(),
                order.disputedAt(),
                order.reconciledAt(),
                order.reconcileStatus(),
                order.paidAt(),
                order.createdAt()
        );
    }
}
