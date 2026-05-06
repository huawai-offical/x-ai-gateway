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
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthIdentityResponse;
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthProviderResponse;
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthStartRequest;
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthStartResponse;
import com.prodigalgal.xaigateway.portal.api.PortalSocialOAuthUnlinkRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class PortalSocialOAuthService {

    private final PortalSocialOauthSessionRepository sessionRepository;
    private final GatewayUserSocialIdentityRepository socialIdentityRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final PortalAuthService portalAuthService;
    private final GatewayProperties gatewayProperties;
    private final Map<SocialOAuthProvider, SocialOAuthProfileClient> profileClients;

    @Autowired
    public PortalSocialOAuthService(
            PortalSocialOauthSessionRepository sessionRepository,
            GatewayUserSocialIdentityRepository socialIdentityRepository,
            GatewayUserRepository gatewayUserRepository,
            PortalAuthService portalAuthService,
            GatewayProperties gatewayProperties,
            List<SocialOAuthProfileClient> profileClients) {
        this.sessionRepository = sessionRepository;
        this.socialIdentityRepository = socialIdentityRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.portalAuthService = portalAuthService;
        this.gatewayProperties = gatewayProperties;
        this.profileClients = profileClients.stream()
                .sorted(java.util.Comparator.comparingInt(SocialOAuthProfileClient::priority))
                .flatMap(client -> Arrays.stream(SocialOAuthProvider.values())
                        .filter(client::supports)
                        .map(provider -> Map.entry(provider, client)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));
    }

    public PortalSocialOAuthService(
            PortalSocialOauthSessionRepository sessionRepository,
            GatewayUserSocialIdentityRepository socialIdentityRepository,
            GatewayUserRepository gatewayUserRepository,
            PortalAuthService portalAuthService,
            GatewayProperties gatewayProperties) {
        this(
                sessionRepository,
                socialIdentityRepository,
                gatewayUserRepository,
                portalAuthService,
                gatewayProperties,
                List.of(new LocalSocialOAuthProfileClient())
        );
    }

    @Transactional(readOnly = true)
    public List<PortalSocialOAuthProviderResponse> providers() {
        return Arrays.stream(SocialOAuthProvider.values())
                .map(provider -> new PortalSocialOAuthProviderResponse(
                        provider.wireName(),
                        provider.displayName(),
                        provider.authorizationEndpoint(),
                        provider.defaultScopes()))
                .toList();
    }

    public PortalSocialOAuthStartResponse start(String providerName, PortalSocialOAuthStartRequest request) {
        SocialOAuthProvider provider = SocialOAuthProvider.fromWireName(providerName);
        String state = "pso_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(900);
        String redirectPath = request == null || request.redirectPath() == null || request.redirectPath().isBlank()
                ? "/portal/oauth/" + provider.wireName() + "/callback"
                : request.redirectPath().trim();
        List<String> scopes = request == null || request.scopes() == null || request.scopes().isEmpty()
                ? provider.defaultScopes()
                : request.scopes().stream().filter(scope -> scope != null && !scope.isBlank()).map(String::trim).toList();
        String callbackUrl = publicBaseUrl() + "/portal/auth/oauth/" + provider.wireName() + "/callback";
        String clientId = request == null || request.clientId() == null || request.clientId().isBlank()
                ? defaultString(configuredClientId(provider), "mock-" + provider.wireName() + "-client")
                : request.clientId().trim();
        String codeVerifier = "cv_" + UUID.randomUUID().toString().replace("-", "");
        String authorizationUrl = provider.authorizationEndpoint()
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callbackUrl)
                + "&scope=" + encode(String.join(" ", scopes))
                + "&state=" + encode(state)
                + pkceAuthorizationParameters(provider, codeVerifier);

        PortalSocialOauthSessionEntity session = new PortalSocialOauthSessionEntity();
        session.setState(state);
        session.setProvider(provider);
        session.setStatus("STARTED");
        session.setAuthorizationUrl(authorizationUrl);
        session.setRedirectPath(redirectPath);
        session.setCodeVerifier(codeVerifier);
        session.setMetadataJson("{\"scopes\":\"" + String.join(" ", scopes) + "\"}");
        session.setExpiresAt(expiresAt);
        sessionRepository.save(session);
        return new PortalSocialOAuthStartResponse(provider.wireName(), state, authorizationUrl, expiresAt);
    }

    public Mono<PortalSessionResponse> complete(
            String providerName,
            PortalSocialOAuthCallbackRequest request,
            ServerWebExchange exchange) {
        SocialOAuthProvider provider = SocialOAuthProvider.fromWireName(providerName);
        PortalSocialOauthSessionEntity session = sessionRepository.findByState(request.state())
                .orElseThrow(() -> new IllegalArgumentException("未找到社交 OAuth 会话。"));
        if (session.getProvider() != provider) {
            throw new IllegalArgumentException("社交 OAuth provider 与 state 不匹配。");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("社交 OAuth 会话已过期。");
        }
        SocialOAuthProfile profile = resolveProfile(provider, request, session);
        String externalSubject = required(profile.externalSubject(), "社交 OAuth 外部用户标识");
        GatewayUserEntity user = socialIdentityRepository.findByProviderAndExternalSubject(provider, externalSubject)
                .map(identity -> updateExistingIdentity(identity, profile))
                .orElseGet(() -> bindNewIdentity(provider, profile));
        session.setStatus("COMPLETED");
        session.setMetadataJson(defaultString(profile.metadataJson(), session.getMetadataJson()));
        sessionRepository.save(session);
        return portalAuthService.authenticateExternalUser(user, exchange);
    }

    @Transactional(readOnly = true)
    public List<PortalSocialOAuthIdentityResponse> identities(WebSession session) {
        GatewayUserEntity user = portalAuthService.requireCurrentPortalUser(session);
        return socialIdentityRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toIdentityResponse)
                .toList();
    }

    public List<PortalSocialOAuthIdentityResponse> unlink(
            WebSession session,
            String providerName,
            PortalSocialOAuthUnlinkRequest request) {
        GatewayUserEntity user = portalAuthService.requireCurrentPortalUser(session);
        SocialOAuthProvider provider = SocialOAuthProvider.fromWireName(providerName);
        GatewayUserSocialIdentityEntity identity = resolveIdentityForUnlink(user, provider, request);
        socialIdentityRepository.delete(identity);
        return socialIdentityRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> identity.getId() == null || !identity.getId().equals(item.getId()))
                .map(this::toIdentityResponse)
                .toList();
    }

    private SocialOAuthProfile resolveProfile(
            SocialOAuthProvider provider,
            PortalSocialOAuthCallbackRequest request,
            PortalSocialOauthSessionEntity session) {
        if (request.externalSubject() != null && !request.externalSubject().isBlank()) {
            return new SocialOAuthProfile(
                    provider,
                    request.externalSubject().trim(),
                    request.email(),
                    request.displayName(),
                    defaultString(request.metadataJson(), "{}")
            );
        }
        SocialOAuthProfileClient client = profileClients.get(provider);
        if (client == null) {
            throw new IllegalStateException("未配置社交 OAuth profile client：" + provider.wireName());
        }
        return client.exchange(new SocialOAuthTokenExchangeRequest(
                provider,
                request.code(),
                request.state(),
                publicBaseUrl() + "/portal/auth/oauth/" + provider.wireName() + "/callback",
                session.getCodeVerifier(),
                request.externalSubject(),
                request.email(),
                request.displayName(),
                request.metadataJson()
        ));
    }

    private GatewayUserEntity bindNewIdentity(
            SocialOAuthProvider provider,
            SocialOAuthProfile profile) {
        String externalSubject = required(profile.externalSubject(), "社交 OAuth 外部用户标识");
        String email = normalizedEmailOrSynthetic(profile.email(), provider, externalSubject);
        GatewayUserEntity user = gatewayUserRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    GatewayUserEntity created = new GatewayUserEntity();
                    created.setEmail(email);
                    created.setDisplayName(blankToNull(profile.displayName()));
                    created.setActive(true);
                    return gatewayUserRepository.save(created);
                });

        GatewayUserSocialIdentityEntity identity = new GatewayUserSocialIdentityEntity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setExternalSubject(externalSubject);
        identity.setEmail(email);
        identity.setDisplayName(blankToNull(profile.displayName()));
        identity.setLastLoginAt(Instant.now());
        identity.setMetadataJson(defaultString(profile.metadataJson(), "{}"));
        socialIdentityRepository.save(identity);
        return user;
    }

    private GatewayUserEntity updateExistingIdentity(
            GatewayUserSocialIdentityEntity identity,
            SocialOAuthProfile profile) {
        identity.setLastLoginAt(Instant.now());
        if (profile.email() != null && !profile.email().isBlank()) {
            identity.setEmail(profile.email().trim().toLowerCase(Locale.ROOT));
        }
        if (profile.displayName() != null && !profile.displayName().isBlank()) {
            identity.setDisplayName(profile.displayName().trim());
        }
        identity.setMetadataJson(defaultString(profile.metadataJson(), identity.getMetadataJson()));
        socialIdentityRepository.save(identity);
        return identity.getUser();
    }

    private GatewayUserSocialIdentityEntity resolveIdentityForUnlink(
            GatewayUserEntity user,
            SocialOAuthProvider provider,
            PortalSocialOAuthUnlinkRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("解绑社交 OAuth 身份需要提供 identityId 或 externalSubject。");
        }
        if (request.identityId() != null) {
            GatewayUserSocialIdentityEntity identity = socialIdentityRepository.findById(request.identityId())
                    .orElseThrow(() -> new IllegalArgumentException("未找到社交 OAuth 身份。"));
            if (!user.getId().equals(identity.getUser().getId()) || identity.getProvider() != provider) {
                throw new IllegalArgumentException("无权解绑该社交 OAuth 身份。");
            }
            return identity;
        }
        if (request.externalSubject() != null && !request.externalSubject().isBlank()) {
            return socialIdentityRepository
                    .findByUser_IdAndProviderAndExternalSubject(user.getId(), provider, request.externalSubject().trim())
                    .orElseThrow(() -> new IllegalArgumentException("未找到社交 OAuth 身份。"));
        }
        throw new IllegalArgumentException("解绑社交 OAuth 身份需要提供 identityId 或 externalSubject。");
    }

    private PortalSocialOAuthIdentityResponse toIdentityResponse(GatewayUserSocialIdentityEntity entity) {
        return new PortalSocialOAuthIdentityResponse(
                entity.getId(),
                entity.getProvider().wireName(),
                entity.getExternalSubject(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizedEmailOrSynthetic(String email, SocialOAuthProvider provider, String externalSubject) {
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        String subject = externalSubject.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return subject + "@" + provider.wireName() + ".oauth.local";
    }

    private String publicBaseUrl() {
        return gatewayProperties.getWeb().getPublicBaseUrl().replaceAll("/+$", "");
    }

    private String configuredClientId(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialClientId();
            case QQ -> gatewayProperties.getOauth().getQqSocialClientId();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialClientId();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialClientId();
            case META -> gatewayProperties.getOauth().getMetaSocialClientId();
            case X -> gatewayProperties.getOauth().getXSocialClientId();
        };
    }

    private String pkceAuthorizationParameters(SocialOAuthProvider provider, String codeVerifier) {
        if (provider != SocialOAuthProvider.X) {
            return "";
        }
        return "&code_challenge=" + encode(codeChallenge(codeVerifier)) + "&code_challenge_method=S256";
    }

    private String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空。");
        }
        return value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
