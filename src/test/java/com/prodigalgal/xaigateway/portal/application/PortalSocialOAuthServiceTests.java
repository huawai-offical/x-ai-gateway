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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
