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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class PortalSocialOAuthService {

    private final PortalSocialOauthSessionRepository sessionRepository;
    private final GatewayUserSocialIdentityRepository socialIdentityRepository;
    private final GatewayUserRepository gatewayUserRepository;
    private final PortalAuthService portalAuthService;
    private final GatewayProperties gatewayProperties;
    private final PortalSocialOAuthConfigService socialOAuthConfigService;
    private final ObjectProvider<PortalSecurityService> portalSecurityServiceProvider;
    private final ObjectProvider<InvitationCodeRedemptionService> invitationCodeRedemptionServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<SocialOAuthProfileClient> profileClients;

    @Autowired
    public PortalSocialOAuthService(
            PortalSocialOauthSessionRepository sessionRepository,
            GatewayUserSocialIdentityRepository socialIdentityRepository,
            GatewayUserRepository gatewayUserRepository,
            PortalAuthService portalAuthService,
            GatewayProperties gatewayProperties,
            PortalSocialOAuthConfigService socialOAuthConfigService,
            ObjectProvider<PortalSecurityService> portalSecurityServiceProvider,
            ObjectProvider<InvitationCodeRedemptionService> invitationCodeRedemptionServiceProvider,
            List<SocialOAuthProfileClient> profileClients) {
        this.sessionRepository = sessionRepository;
        this.socialIdentityRepository = socialIdentityRepository;
        this.gatewayUserRepository = gatewayUserRepository;
        this.portalAuthService = portalAuthService;
        this.gatewayProperties = gatewayProperties;
        this.socialOAuthConfigService = socialOAuthConfigService;
        this.portalSecurityServiceProvider = portalSecurityServiceProvider;
        this.invitationCodeRedemptionServiceProvider = invitationCodeRedemptionServiceProvider;
        this.profileClients = profileClients == null
                ? List.of()
                : profileClients.stream()
                        .sorted(Comparator.comparingInt(SocialOAuthProfileClient::priority))
                        .toList();
    }

    public PortalSocialOAuthService(
            PortalSocialOauthSessionRepository sessionRepository,
            GatewayUserSocialIdentityRepository socialIdentityRepository,
            GatewayUserRepository gatewayUserRepository,
            PortalAuthService portalAuthService,
            GatewayProperties gatewayProperties,
            List<SocialOAuthProfileClient> profileClients) {
        this(
                sessionRepository,
                socialIdentityRepository,
                gatewayUserRepository,
                portalAuthService,
                gatewayProperties,
                null,
                null,
                null,
                profileClients
        );
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
                null,
                null,
                null,
                List.of(new LocalSocialOAuthProfileClient())
        );
    }

    public PortalSocialOAuthService(
            PortalSocialOauthSessionRepository sessionRepository,
            GatewayUserSocialIdentityRepository socialIdentityRepository,
            GatewayUserRepository gatewayUserRepository,
            PortalAuthService portalAuthService,
            GatewayProperties gatewayProperties,
            PortalSocialOAuthConfigService socialOAuthConfigService,
            ObjectProvider<PortalSecurityService> portalSecurityServiceProvider,
            List<SocialOAuthProfileClient> profileClients) {
        this(
                sessionRepository,
                socialIdentityRepository,
                gatewayUserRepository,
                portalAuthService,
                gatewayProperties,
                socialOAuthConfigService,
                portalSecurityServiceProvider,
                null,
                profileClients
        );
    }

    @Transactional(readOnly = true)
    public List<PortalSocialOAuthProviderResponse> providers() {
        PortalSocialOAuthRuntimeConfig runtimeConfig = runtimeConfig();
        return Arrays.stream(SocialOAuthProvider.values())
                .filter(runtimeConfig::enabledForLogin)
                .map(provider -> new PortalSocialOAuthProviderResponse(
                        provider.wireName(),
                        provider.displayName(),
                        provider.authorizationEndpoint(),
                        runtimeConfig.provider(provider).scopes()))
                .toList();
    }

    public PortalSocialOAuthStartResponse start(String providerName, PortalSocialOAuthStartRequest request) {
        SocialOAuthProvider provider = SocialOAuthProvider.fromWireName(providerName);
        PortalSocialOAuthRuntimeConfig.ProviderConfig providerConfig = requireEnabledConfig(provider);
        String state = "pso_" + UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(900);
        String redirectPath = request == null || request.redirectPath() == null || request.redirectPath().isBlank()
                ? "/portal/security"
                : request.redirectPath().trim();
        List<String> scopes = socialOAuthConfigService == null
                && request != null
                && request.scopes() != null
                && !request.scopes().isEmpty()
                        ? request.scopes().stream().filter(scope -> scope != null && !scope.isBlank()).map(String::trim).toList()
                        : providerConfig.scopes();
        String callbackUrl = publicBaseUrl() + "/portal/auth/oauth/" + provider.wireName() + "/callback";
        String clientId = socialOAuthConfigService == null && request != null && request.clientId() != null && !request.clientId().isBlank()
                ? request.clientId().trim()
                : providerConfig.clientId();
        String codeVerifier = "cv_" + UUID.randomUUID().toString().replace("-", "");
        String authorizationUrl = provider.authorizationEndpoint()
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callbackUrl)
                + "&scope=" + encode(String.join(" ", scopes))
                + "&state=" + encode(state)
                + pkceAuthorizationParameters(provider, codeVerifier);
        String inviteCode = normalizeInviteCode(request == null ? null : request.inviteCode());

        PortalSocialOauthSessionEntity session = new PortalSocialOauthSessionEntity();
        session.setState(state);
        session.setProvider(provider);
        session.setStatus("STARTED");
        session.setAuthorizationUrl(authorizationUrl);
        session.setRedirectPath(redirectPath);
        session.setCodeVerifier(codeVerifier);
        session.setMetadataJson(oauthSessionMetadata(String.join(" ", scopes), inviteCode));
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
        PortalSocialOAuthRuntimeConfig.ProviderConfig providerConfig = requireEnabledConfig(provider);
        SocialOAuthProfile profile = resolveProfile(provider, request, session, providerConfig);
        String externalSubject = required(profile.externalSubject(), "社交 OAuth 外部用户标识");
        return exchange.getSession().flatMap(webSession -> {
            GatewayUserEntity currentUser = currentPortalUserOrNull(webSession);
            GatewayUserEntity user = currentUser == null
                    ? resolveLoginUser(provider, profile, externalSubject, inviteCodeFromSession(session))
                    : bindIdentityToCurrentUser(provider, profile, externalSubject, currentUser);
            session.setStatus("COMPLETED");
            session.setMetadataJson(mergeCompletionMetadata(session.getMetadataJson(), profile.metadataJson()));
            sessionRepository.save(session);
            return portalAuthService.authenticateExternalUser(user, exchange);
        });
    }

    public Mono<ResponseEntity<Void>> completeRedirect(
            String providerName,
            String state,
            String code,
            String error,
            ServerWebExchange exchange) {
        String fallbackRedirectPath = "/portal/security";
        if (state != null && !state.isBlank()) {
            fallbackRedirectPath = sessionRepository.findByState(state)
                    .map(PortalSocialOauthSessionEntity::getRedirectPath)
                    .filter(path -> path != null && !path.isBlank())
                    .orElse(fallbackRedirectPath);
        }
        String redirectPath = fallbackRedirectPath;
        if (error != null && !error.isBlank()) {
            return Mono.just(redirect(appendResult(redirectPath, "oauth_error", error)));
        }
        if (state == null || state.isBlank() || code == null || code.isBlank()) {
            return Mono.just(redirect(appendResult(redirectPath, "oauth_error", "missing_code_or_state")));
        }
        return complete(providerName, new PortalSocialOAuthCallbackRequest(state, code, null, null, null, null), exchange)
                .thenReturn(redirect(appendResult(redirectPath, "oauth", "success")))
                .onErrorResume(exception -> Mono.just(redirect(appendResult(redirectPath, "oauth_error", exception.getMessage()))));
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
            PortalSocialOauthSessionEntity session,
            PortalSocialOAuthRuntimeConfig.ProviderConfig providerConfig) {
        if (socialOAuthConfigService == null && request.externalSubject() != null && !request.externalSubject().isBlank()) {
            return new SocialOAuthProfile(
                    provider,
                    request.externalSubject().trim(),
                    request.email(),
                    request.displayName(),
                    defaultString(request.metadataJson(), "{}")
            );
        }
        SocialOAuthProfileClient client = profileClients.stream()
                .filter(candidate -> candidate.supports(provider))
                .findFirst()
                .orElse(null);
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
                request.metadataJson(),
                providerConfig.clientId(),
                providerConfig.clientSecret(),
                providerConfig.tokenEndpoint(),
                providerConfig.userInfoEndpoint(),
                providerConfig.jwksUri()
        ));
    }

    private GatewayUserEntity resolveLoginUser(
            SocialOAuthProvider provider,
            SocialOAuthProfile profile,
            String externalSubject,
            String inviteCode) {
        return socialIdentityRepository.findByProviderAndExternalSubject(provider, externalSubject)
                .map(identity -> updateExistingIdentity(identity, profile))
                .orElseGet(() -> bindNewIdentity(provider, profile, inviteCode));
    }

    private GatewayUserEntity bindIdentityToCurrentUser(
            SocialOAuthProvider provider,
            SocialOAuthProfile profile,
            String externalSubject,
            GatewayUserEntity currentUser) {
        return socialIdentityRepository.findByProviderAndExternalSubject(provider, externalSubject)
                .map(identity -> {
                    if (!currentUser.getId().equals(identity.getUser().getId())) {
                        throw new IllegalArgumentException("该社交 OAuth 身份已绑定其它用户。");
                    }
                    return updateExistingIdentity(identity, profile);
                })
                .orElseGet(() -> createIdentity(currentUser, provider, profile, normalizedEmailOrNull(profile.email()), externalSubject));
    }

    private GatewayUserEntity bindNewIdentity(
            SocialOAuthProvider provider,
            SocialOAuthProfile profile,
            String inviteCode) {
        String externalSubject = required(profile.externalSubject(), "社交 OAuth 外部用户标识");
        String email = normalizedEmailOrSynthetic(profile.email(), provider, externalSubject);
        GatewayUserEntity user = gatewayUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            portalSecurityService().ifPresent(service -> service.verifyRegistrationPolicy(
                    email,
                    inviteCode,
                    PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH
            ));
            GatewayUserEntity created = new GatewayUserEntity();
            created.setEmail(email);
            created.setDisplayName(blankToNull(profile.displayName()));
            created.setActive(true);
            user = gatewayUserRepository.save(created);
            redeemInvitationCodeIfPresent(inviteCode, user, email);
        }

        return createIdentity(user, provider, profile, email, externalSubject);
    }

    private GatewayUserEntity createIdentity(
            GatewayUserEntity user,
            SocialOAuthProvider provider,
            SocialOAuthProfile profile,
            String email,
            String externalSubject) {
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

    private String normalizedEmailOrNull(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private PortalSocialOAuthRuntimeConfig runtimeConfig() {
        if (socialOAuthConfigService != null) {
            return socialOAuthConfigService.getRuntimeConfig();
        }
        Map<SocialOAuthProvider, PortalSocialOAuthRuntimeConfig.ProviderConfig> providers = new EnumMap<>(SocialOAuthProvider.class);
        for (SocialOAuthProvider provider : SocialOAuthProvider.values()) {
            providers.put(provider, new PortalSocialOAuthRuntimeConfig.ProviderConfig(
                    provider,
                    true,
                    defaultString(configuredClientId(provider), "mock-" + provider.wireName() + "-client"),
                    defaultString(configuredClientSecret(provider), "mock-" + provider.wireName() + "-secret"),
                    configuredTokenEndpoint(provider),
                    configuredUserInfoEndpoint(provider),
                    configuredJwksUri(provider),
                    provider.defaultScopes()
            ));
        }
        return new PortalSocialOAuthRuntimeConfig(true, providers);
    }

    private PortalSocialOAuthRuntimeConfig.ProviderConfig requireEnabledConfig(SocialOAuthProvider provider) {
        PortalSocialOAuthRuntimeConfig runtimeConfig = runtimeConfig();
        PortalSocialOAuthRuntimeConfig.ProviderConfig providerConfig = runtimeConfig.provider(provider);
        if (providerConfig == null || !runtimeConfig.enabledForLogin(provider)) {
            throw new IllegalStateException("社交 OAuth provider 未启用或未配置：" + provider.wireName());
        }
        return providerConfig;
    }

    private GatewayUserEntity currentPortalUserOrNull(WebSession session) {
        try {
            return portalAuthService.requireCurrentPortalUser(session);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private java.util.Optional<PortalSecurityService> portalSecurityService() {
        return portalSecurityServiceProvider == null
                ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(portalSecurityServiceProvider.getIfAvailable());
    }

    private java.util.Optional<InvitationCodeRedemptionService> invitationCodeRedemptionService() {
        return invitationCodeRedemptionServiceProvider == null
                ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(invitationCodeRedemptionServiceProvider.getIfAvailable());
    }

    private void redeemInvitationCodeIfPresent(String inviteCode, GatewayUserEntity user, String email) {
        String normalizedInviteCode = normalizeInviteCode(inviteCode);
        if (normalizedInviteCode == null) {
            return;
        }
        InvitationCodeRedemptionService service = invitationCodeRedemptionService()
                .orElseThrow(() -> new IllegalStateException("邀请码系统未配置，无法完成社交 OAuth 邀请码注册。"));
        service.redeemForRegistration(
                normalizedInviteCode,
                user,
                email,
                PortalSecurityService.REGISTRATION_CHANNEL_SOCIAL_OAUTH,
                "PORTAL_SOCIAL_OAUTH"
        );
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
    }

    private String appendResult(String redirectPath, String key, String value) {
        String base = redirectPath == null || redirectPath.isBlank() ? "/portal/security" : redirectPath.trim();
        return base + (base.contains("?") ? "&" : "?") + encode(key) + "=" + encode(value);
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

    private String configuredClientSecret(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialClientSecret();
            case QQ -> gatewayProperties.getOauth().getQqSocialClientSecret();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialClientSecret();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialClientSecret();
            case META -> gatewayProperties.getOauth().getMetaSocialClientSecret();
            case X -> gatewayProperties.getOauth().getXSocialClientSecret();
        };
    }

    private String configuredTokenEndpoint(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialTokenEndpoint();
            case QQ -> gatewayProperties.getOauth().getQqSocialTokenEndpoint();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialTokenEndpoint();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialTokenEndpoint();
            case META -> gatewayProperties.getOauth().getMetaSocialTokenEndpoint();
            case X -> gatewayProperties.getOauth().getXSocialTokenEndpoint();
        };
    }

    private String configuredUserInfoEndpoint(SocialOAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> gatewayProperties.getOauth().getGoogleSocialUserInfoEndpoint();
            case QQ -> gatewayProperties.getOauth().getQqSocialUserInfoEndpoint();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialUserInfoEndpoint();
            case GITHUB -> gatewayProperties.getOauth().getGithubSocialUserEndpoint();
            case META -> gatewayProperties.getOauth().getMetaSocialUserInfoEndpoint();
            case X -> gatewayProperties.getOauth().getXSocialUserInfoEndpoint();
        };
    }

    private String configuredJwksUri(SocialOAuthProvider provider) {
        return provider == SocialOAuthProvider.GOOGLE ? gatewayProperties.getOauth().getGoogleSocialJwksUri() : null;
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

    private String normalizeInviteCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String oauthSessionMetadata(String scopes, String inviteCode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scopes", scopes);
        metadata.put("mode", "login_or_bind");
        if (inviteCode != null) {
            metadata.put("inviteCode", inviteCode);
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String inviteCodeFromSession(PortalSocialOauthSessionEntity session) {
        return inviteCodeFromMetadata(session == null ? null : session.getMetadataJson());
    }

    private String mergeCompletionMetadata(String sessionMetadataJson, String profileMetadataJson) {
        String inviteCode = inviteCodeFromMetadata(sessionMetadataJson);
        String base = defaultString(profileMetadataJson, defaultString(sessionMetadataJson, "{}"));
        if (inviteCode == null || base.contains("\"inviteCode\"")) {
            return base;
        }
        if (base == null || base.isBlank() || !base.trim().startsWith("{") || !base.trim().endsWith("}")) {
            return oauthSessionMetadata("", inviteCode);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profileMetadataJson", base);
        metadata.put("inviteCode", inviteCode);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ignored) {
            return oauthSessionMetadata("", inviteCode);
        }
    }

    private String inviteCodeFromMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            String inviteCode = objectMapper.readTree(metadataJson).path("inviteCode").asText(null);
            return normalizeInviteCode(inviteCode);
        } catch (Exception ignored) {
            return null;
        }
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
