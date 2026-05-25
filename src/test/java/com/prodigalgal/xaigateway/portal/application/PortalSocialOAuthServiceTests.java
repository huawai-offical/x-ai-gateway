package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserSocialIdentityEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.PortalSocialOauthSessionEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserSocialIdentityRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.PortalSocialOauthSessionRepository;
import com.prodigalgal.xaigateway.portal.api.PortalSessionResponse;
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthCallbackRequest;
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthStartRequest;
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthUnlinkRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class PortalSocialOAuthServiceTests {

    @Test
    void shouldListProvidersStartGithubOAuthAndBindUserOnCallback() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getWeb().setPublicBaseUrl("https://gateway.example.com");
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties
        );
        Mockito.when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PortalSocialOauthSessionEntity session = invocation.getArgument(0);
            if (session.getId() == null) {
                ReflectionTestUtils.setField(session, "id", 17L);
            }
            Mockito.when(sessionRepository.findByState(session.getState())).thenReturn(Optional.of(session));
            return session;
        });
        Mockito.when(identityRepository.findByProviderAndExternalSubject(SocialOAuthProvider.GITHUB, "github-42"))
                .thenReturn(Optional.empty());
        Mockito.when(userRepository.findByEmailIgnoreCase("octo@example.com")).thenReturn(Optional.empty());
        Mockito.when(userRepository.save(any())).thenAnswer(invocation -> {
            GatewayUserEntity user = invocation.getArgument(0);
            if (user.getId() == null) {
                ReflectionTestUtils.setField(user, "id", 42L);
            }
            return user;
        });
        Mockito.when(identityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(portalAuthService.authenticateExternalUser(any(), any()))
                .thenReturn(Mono.just(new PortalSessionResponse(
                        true,
                        42L,
                        "octo@example.com",
                        "Octo",
                        Instant.parse("2026-05-01T08:00:00Z"),
                        Instant.parse("2026-05-01T20:00:00Z")
                )));

        var providers = service.providers();
        var start = service.start("github", new PortalSocialOAuthStartRequest("gh-client", "/portal/callback", List.of("read:user")));
        var xStart = service.start("x", new PortalSocialOAuthStartRequest("x-client", "/portal/callback", List.of("users.read")));
        var session = service.complete(
                "github",
                new PortalSocialOAuthCallbackRequest(start.state(), "code-1", "github-42", "octo@example.com", "Octo", "{}"),
                MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build())
        ).block();

        assertTrue(providers.stream().anyMatch(provider -> provider.provider().equals("google")));
        assertTrue(providers.stream().anyMatch(provider -> provider.provider().equals("x")));
        assertEquals("github", start.provider());
        assertTrue(start.authorizationUrl().contains("github.com/login/oauth/authorize"));
        assertTrue(start.authorizationUrl().contains("client_id=gh-client"));
        assertTrue(xStart.authorizationUrl().contains("code_challenge_method=S256"));
        assertTrue(session.authenticated());
        ArgumentCaptor<GatewayUserSocialIdentityEntity> identityCaptor = ArgumentCaptor.forClass(GatewayUserSocialIdentityEntity.class);
        Mockito.verify(identityRepository).save(identityCaptor.capture());
        assertEquals(SocialOAuthProvider.GITHUB, identityCaptor.getValue().getProvider());
        assertEquals("github-42", identityCaptor.getValue().getExternalSubject());
    }

    @Test
    void shouldExchangeCodeForProfileWhenCallbackMissesSubject() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getWeb().setPublicBaseUrl("https://gateway.example.com");
        SocialOAuthProfileClient profileClient = new SocialOAuthProfileClient() {
            @Override
            public boolean supports(SocialOAuthProvider provider) {
                return provider == SocialOAuthProvider.GOOGLE;
            }

            @Override
            public SocialOAuthProfile exchange(SocialOAuthTokenExchangeRequest request) {
                assertEquals("code-real", request.code());
                assertEquals(SocialOAuthProvider.GOOGLE, request.provider());
                return new SocialOAuthProfile(
                        SocialOAuthProvider.GOOGLE,
                        "google-real-7",
                        "real@example.com",
                        "Real User",
                        "{\"exchange\":\"test\"}"
                );
            }
        };
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties,
                List.of(profileClient)
        );
        Mockito.when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PortalSocialOauthSessionEntity session = invocation.getArgument(0);
            if (session.getId() == null) {
                ReflectionTestUtils.setField(session, "id", 27L);
            }
            Mockito.when(sessionRepository.findByState(session.getState())).thenReturn(Optional.of(session));
            return session;
        });
        Mockito.when(identityRepository.findByProviderAndExternalSubject(SocialOAuthProvider.GOOGLE, "google-real-7"))
                .thenReturn(Optional.empty());
        Mockito.when(userRepository.findByEmailIgnoreCase("real@example.com")).thenReturn(Optional.empty());
        Mockito.when(userRepository.save(any())).thenAnswer(invocation -> {
            GatewayUserEntity user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 77L);
            return user;
        });
        Mockito.when(identityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(portalAuthService.authenticateExternalUser(any(), any()))
                .thenReturn(Mono.just(new PortalSessionResponse(
                        true,
                        77L,
                        "real@example.com",
                        "Real User",
                        Instant.parse("2026-05-01T08:00:00Z"),
                        Instant.parse("2026-05-01T20:00:00Z")
                )));

        var start = service.start("google", new PortalSocialOAuthStartRequest("google-client", "/portal/callback", List.of()));
        var session = service.complete(
                "google",
                new PortalSocialOAuthCallbackRequest(start.state(), "code-real", null, null, null, null),
                MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build())
        ).block();

        assertTrue(session.authenticated());
        ArgumentCaptor<GatewayUserSocialIdentityEntity> identityCaptor = ArgumentCaptor.forClass(GatewayUserSocialIdentityEntity.class);
        Mockito.verify(identityRepository).save(identityCaptor.capture());
        assertEquals("google-real-7", identityCaptor.getValue().getExternalSubject());
        assertEquals("real@example.com", identityCaptor.getValue().getEmail());
    }

    @Test
    void shouldListAndUnlinkCurrentUserSocialIdentity() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        GatewayProperties properties = new GatewayProperties();
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties
        );

        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setEmail("octo@example.com");
        user.setActive(true);

        GatewayUserSocialIdentityEntity identity = new GatewayUserSocialIdentityEntity();
        ReflectionTestUtils.setField(identity, "id", 88L);
        identity.setUser(user);
        identity.setProvider(SocialOAuthProvider.GITHUB);
        identity.setExternalSubject("github-42");
        identity.setEmail("octo@example.com");
        identity.setDisplayName("Octo");
        identity.setLastLoginAt(Instant.parse("2026-05-01T08:00:00Z"));

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build());
        var webSession = exchange.getSession().block();
        Mockito.when(portalAuthService.requireCurrentPortalUser(webSession)).thenReturn(user);
        Mockito.when(identityRepository.findAllByUser_IdOrderByCreatedAtDesc(42L)).thenReturn(List.of(identity));
        Mockito.when(identityRepository.findById(88L)).thenReturn(Optional.of(identity));

        var identities = service.identities(webSession);
        var remaining = service.unlink(webSession, "github", new PortalSocialOAuthUnlinkRequest(88L, null));

        assertEquals(1, identities.size());
        assertEquals("github", identities.getFirst().provider());
        assertTrue(remaining.isEmpty());
        Mockito.verify(identityRepository).delete(identity);
    }

    @Test
    void shouldBindSocialIdentityToCurrentPortalUser() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getWeb().setPublicBaseUrl("https://gateway.example.com");
        SocialOAuthProfileClient profileClient = googleProfileClient("google-current-7", "other@example.com", "Other Email");
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties,
                List.of(profileClient)
        );
        Mockito.when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PortalSocialOauthSessionEntity session = invocation.getArgument(0);
            Mockito.when(sessionRepository.findByState(session.getState())).thenReturn(Optional.of(session));
            return session;
        });
        GatewayUserEntity currentUser = user(91L, "password-user@example.com");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build());
        var webSession = exchange.getSession().block();
        webSession.getAttributes().put("portalUserId", 91L);
        Mockito.when(portalAuthService.requireCurrentPortalUser(webSession)).thenReturn(currentUser);
        Mockito.when(identityRepository.findByProviderAndExternalSubject(SocialOAuthProvider.GOOGLE, "google-current-7"))
                .thenReturn(Optional.empty());
        Mockito.when(identityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(portalAuthService.authenticateExternalUser(any(), any()))
                .thenReturn(Mono.just(new PortalSessionResponse(true, 91L, "password-user@example.com", null, null, null)));

        var start = service.start("google", new PortalSocialOAuthStartRequest("google-client", "/portal/security", List.of()));
        var session = service.complete(
                "google",
                new PortalSocialOAuthCallbackRequest(start.state(), "code-bind", null, null, null, null),
                exchange
        ).block();

        assertTrue(session.authenticated());
        ArgumentCaptor<GatewayUserSocialIdentityEntity> identityCaptor = ArgumentCaptor.forClass(GatewayUserSocialIdentityEntity.class);
        Mockito.verify(identityRepository).save(identityCaptor.capture());
        assertEquals(91L, identityCaptor.getValue().getUser().getId());
        assertEquals("other@example.com", identityCaptor.getValue().getEmail());
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void shouldRejectBindingSocialIdentityOwnedByOtherUser() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getWeb().setPublicBaseUrl("https://gateway.example.com");
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties,
                List.of(googleProfileClient("google-owned-7", "owned@example.com", "Owned"))
        );
        Mockito.when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PortalSocialOauthSessionEntity session = invocation.getArgument(0);
            Mockito.when(sessionRepository.findByState(session.getState())).thenReturn(Optional.of(session));
            return session;
        });
        GatewayUserEntity currentUser = user(91L, "password-user@example.com");
        GatewayUserEntity owner = user(92L, "owner@example.com");
        GatewayUserSocialIdentityEntity existing = new GatewayUserSocialIdentityEntity();
        existing.setUser(owner);
        existing.setProvider(SocialOAuthProvider.GOOGLE);
        existing.setExternalSubject("google-owned-7");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build());
        var webSession = exchange.getSession().block();
        webSession.getAttributes().put("portalUserId", 91L);
        Mockito.when(portalAuthService.requireCurrentPortalUser(webSession)).thenReturn(currentUser);
        Mockito.when(identityRepository.findByProviderAndExternalSubject(SocialOAuthProvider.GOOGLE, "google-owned-7"))
                .thenReturn(Optional.of(existing));

        var start = service.start("google", new PortalSocialOAuthStartRequest("google-client", "/portal/security", List.of()));

        assertThrows(IllegalArgumentException.class, () -> service.complete(
                "google",
                new PortalSocialOAuthCallbackRequest(start.state(), "code-bind", null, null, null, null),
                exchange
        ).block());
    }

    @Test
    void shouldRejectSocialRegistrationWhenChannelClosed() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        PortalSecurityService portalSecurityService = Mockito.mock(PortalSecurityService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getWeb().setPublicBaseUrl("https://gateway.example.com");
        ObjectProvider<PortalSecurityService> securityProvider = new ObjectProvider<>() {
            @Override
            public PortalSecurityService getObject(Object... args) {
                return portalSecurityService;
            }

            @Override
            public PortalSecurityService getIfAvailable() {
                return portalSecurityService;
            }

            @Override
            public PortalSecurityService getIfUnique() {
                return portalSecurityService;
            }

            @Override
            public PortalSecurityService getObject() {
                return portalSecurityService;
            }
        };
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties,
                null,
                securityProvider,
                List.of(googleProfileClient("google-new-7", "new@example.com", "New User"))
        );
        Mockito.when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PortalSocialOauthSessionEntity session = invocation.getArgument(0);
            Mockito.when(sessionRepository.findByState(session.getState())).thenReturn(Optional.of(session));
            return session;
        });
        Mockito.when(portalAuthService.requireCurrentPortalUser(any())).thenThrow(new RuntimeException("anonymous"));
        Mockito.when(identityRepository.findByProviderAndExternalSubject(SocialOAuthProvider.GOOGLE, "google-new-7"))
                .thenReturn(Optional.empty());
        Mockito.when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        Mockito.doThrow(new IllegalArgumentException("当前注册渠道已关闭。"))
                .when(portalSecurityService)
                .verifyRegistrationPolicy("new@example.com", null, PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH);

        var start = service.start("google", new PortalSocialOAuthStartRequest("google-client", "/portal/security", List.of()));

        assertThrows(IllegalArgumentException.class, () -> service.complete(
                "google",
                new PortalSocialOAuthCallbackRequest(start.state(), "code-new", null, null, null, null),
                MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build())
        ).block());
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void shouldRedeemInvitationCodeWhenSocialOAuthCreatesNewUser() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        PortalSecurityService portalSecurityService = Mockito.mock(PortalSecurityService.class);
        InvitationCodeRedemptionService redemptionService = Mockito.mock(InvitationCodeRedemptionService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getWeb().setPublicBaseUrl("https://gateway.example.com");
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties,
                null,
                provider(portalSecurityService),
                provider(redemptionService),
                List.of(googleProfileClient("google-invite-7", "invite@example.com", "Invite User"))
        );
        Mockito.when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PortalSocialOauthSessionEntity session = invocation.getArgument(0);
            Mockito.when(sessionRepository.findByState(session.getState())).thenReturn(Optional.of(session));
            return session;
        });
        Mockito.when(portalAuthService.requireCurrentPortalUser(any())).thenThrow(new RuntimeException("anonymous"));
        Mockito.when(identityRepository.findByProviderAndExternalSubject(SocialOAuthProvider.GOOGLE, "google-invite-7"))
                .thenReturn(Optional.empty());
        Mockito.when(userRepository.findByEmailIgnoreCase("invite@example.com")).thenReturn(Optional.empty());
        Mockito.when(userRepository.save(any())).thenAnswer(invocation -> {
            GatewayUserEntity user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 501L);
            return user;
        });
        Mockito.when(identityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(portalAuthService.authenticateExternalUser(any(), any()))
                .thenReturn(Mono.just(new PortalSessionResponse(true, 501L, "invite@example.com", null, null, null)));

        var start = service.start("google", new PortalSocialOAuthStartRequest("google-client", "/portal", List.of(), " invite-social "));
        var session = service.complete(
                "google",
                new PortalSocialOAuthCallbackRequest(start.state(), "code-invite", null, null, null, null),
                MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build())
        ).block();

        assertTrue(session.authenticated());
        Mockito.verify(portalSecurityService).verifyRegistrationPolicy(
                "invite@example.com",
                "INVITE-SOCIAL",
                PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH
        );
        ArgumentCaptor<GatewayUserEntity> userCaptor = ArgumentCaptor.forClass(GatewayUserEntity.class);
        Mockito.verify(redemptionService).redeemForRegistration(
                Mockito.eq("INVITE-SOCIAL"),
                userCaptor.capture(),
                Mockito.eq("invite@example.com"),
                Mockito.eq(PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH),
                Mockito.eq("PORTAL_SOCIAL_OAUTH")
        );
        assertEquals(501L, userCaptor.getValue().getId());
    }

    @Test
    void shouldNotRedeemInvitationCodeWhenBindingSocialOAuthToCurrentUser() {
        PortalSocialOauthSessionRepository sessionRepository = Mockito.mock(PortalSocialOauthSessionRepository.class);
        GatewayUserSocialIdentityRepository identityRepository = Mockito.mock(GatewayUserSocialIdentityRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        PortalAuthService portalAuthService = Mockito.mock(PortalAuthService.class);
        PortalSecurityService portalSecurityService = Mockito.mock(PortalSecurityService.class);
        InvitationCodeRedemptionService redemptionService = Mockito.mock(InvitationCodeRedemptionService.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getWeb().setPublicBaseUrl("https://gateway.example.com");
        PortalSocialOAuthService service = new PortalSocialOAuthService(
                sessionRepository,
                identityRepository,
                userRepository,
                portalAuthService,
                properties,
                null,
                provider(portalSecurityService),
                provider(redemptionService),
                List.of(googleProfileClient("google-bind-invite-7", "bind@example.com", "Bind User"))
        );
        Mockito.when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PortalSocialOauthSessionEntity session = invocation.getArgument(0);
            Mockito.when(sessionRepository.findByState(session.getState())).thenReturn(Optional.of(session));
            return session;
        });
        GatewayUserEntity currentUser = user(601L, "current@example.com");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/portal").build());
        var webSession = exchange.getSession().block();
        webSession.getAttributes().put("portalUserId", 601L);
        Mockito.when(portalAuthService.requireCurrentPortalUser(webSession)).thenReturn(currentUser);
        Mockito.when(identityRepository.findByProviderAndExternalSubject(SocialOAuthProvider.GOOGLE, "google-bind-invite-7"))
                .thenReturn(Optional.empty());
        Mockito.when(identityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(portalAuthService.authenticateExternalUser(any(), any()))
                .thenReturn(Mono.just(new PortalSessionResponse(true, 601L, "current@example.com", null, null, null)));

        var start = service.start("google", new PortalSocialOAuthStartRequest("google-client", "/portal/security", List.of(), "INVITE-BIND"));
        service.complete(
                "google",
                new PortalSocialOAuthCallbackRequest(start.state(), "code-bind", null, null, null, null),
                exchange
        ).block();

        Mockito.verify(portalSecurityService, Mockito.never()).verifyRegistrationPolicy(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
        );
        Mockito.verify(redemptionService, Mockito.never()).redeemForRegistration(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
        );
    }

    private SocialOAuthProfileClient googleProfileClient(String subject, String email, String displayName) {
        return new SocialOAuthProfileClient() {
            @Override
            public boolean supports(SocialOAuthProvider provider) {
                return provider == SocialOAuthProvider.GOOGLE;
            }

            @Override
            public SocialOAuthProfile exchange(SocialOAuthTokenExchangeRequest request) {
                return new SocialOAuthProfile(SocialOAuthProvider.GOOGLE, subject, email, displayName, "{}");
            }
        };
    }

    private GatewayUserEntity user(Long id, String email) {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setActive(true);
        return user;
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
