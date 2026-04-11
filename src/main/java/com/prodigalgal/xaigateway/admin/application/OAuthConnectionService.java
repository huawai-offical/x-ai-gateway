package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OauthStartResponse;
import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.persistence.entity.OauthAuthorizationSessionEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.UpstreamAccountPoolEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.OauthAuthorizationSessionRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountPoolRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.UpstreamAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class OAuthConnectionService {

    private final OauthAuthorizationSessionRepository oauthAuthorizationSessionRepository;
    private final UpstreamAccountPoolRepository upstreamAccountPoolRepository;
    private final UpstreamAccountRepository upstreamAccountRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final GatewayProperties gatewayProperties;

    public OAuthConnectionService(
            OauthAuthorizationSessionRepository oauthAuthorizationSessionRepository,
            UpstreamAccountPoolRepository upstreamAccountPoolRepository,
            UpstreamAccountRepository upstreamAccountRepository,
            CredentialCryptoService credentialCryptoService,
            GatewayProperties gatewayProperties) {
        this.oauthAuthorizationSessionRepository = oauthAuthorizationSessionRepository;
        this.upstreamAccountPoolRepository = upstreamAccountPoolRepository;
        this.upstreamAccountRepository = upstreamAccountRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.gatewayProperties = gatewayProperties;
    }

    public OauthStartResponse start(UpstreamAccountProviderType providerType, Long poolId, String redirectPath) {
        OauthAuthorizationSessionEntity session = new OauthAuthorizationSessionEntity();
        String sessionKey = "oas_" + UUID.randomUUID().toString().replace("-", "");
        session.setSessionKey(sessionKey);
        session.setProviderType(providerType);
        session.setPoolId(poolId);
        session.setStatus("started");
        session.setCodeVerifier("cv_" + UUID.randomUUID().toString().replace("-", ""));
        session.setRedirectPath(redirectPath == null || redirectPath.isBlank()
                ? "/accounts/callback/" + providerType.name().toLowerCase()
                : redirectPath);
        session.setExpiresAt(Instant.now().plusSeconds(900));

        String callbackUrl = gatewayProperties.getWeb().getPublicBaseUrl().replaceAll("/+$", "")
                + "/admin/oauth/" + providerType.name().toLowerCase() + "/callback";
        String authUrl = resolveAuthBaseUrl(providerType)
                + "?response_type=code&client_id=" + encode(resolveClientId(providerType))
                + "&redirect_uri=" + encode(callbackUrl)
                + "&state=" + encode(sessionKey);
        session.setAuthorizationUrl(authUrl);
        oauthAuthorizationSessionRepository.save(session);
        return new OauthStartResponse(sessionKey, authUrl);
    }

    public String complete(UpstreamAccountProviderType providerType, String code, String state) {
        OauthAuthorizationSessionEntity session = oauthAuthorizationSessionRepository.findBySessionKey(state)
                .orElseThrow(() -> new IllegalArgumentException("未找到 OAuth 会话。"));
        if (session.getProviderType() != providerType) {
            throw new IllegalArgumentException("OAuth provider 与会话不匹配。");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("OAuth 会话已过期。");
        }

        UpstreamAccountPoolEntity pool = upstreamAccountPoolRepository.findById(session.getPoolId())
                .orElseThrow(() -> new IllegalArgumentException("未找到账号池。"));

        UpstreamAccountEntity account = new UpstreamAccountEntity();
        account.setPool(pool);
        account.setProviderType(providerType);
        account.setAccountName(providerType.name().toLowerCase() + "-" + code);
        account.setExternalAccountId(providerType.name().toLowerCase() + ":" + code);
        account.setAccessTokenCiphertext(credentialCryptoService.encrypt("access_" + code));
        account.setRefreshTokenCiphertext(credentialCryptoService.encrypt("refresh_" + code));
        account.setLastRefreshAt(Instant.now());
        account.setMetadataJson(Map.of("sessionKey", state, "provider", providerType.name()).toString());
        upstreamAccountRepository.save(account);

        session.setStatus("completed");
        oauthAuthorizationSessionRepository.save(session);

        return gatewayProperties.getWeb().getPublicBaseUrl().replaceAll("/+$", "")
                + session.getRedirectPath()
                + "?status=success&sessionKey=" + encode(session.getSessionKey())
                + "&accountId=" + account.getId();
    }

    private String resolveAuthBaseUrl(UpstreamAccountProviderType providerType) {
        return switch (providerType) {
            case OPENAI_OAUTH -> gatewayProperties.getOauth().getOpenaiAuthBaseUrl();
            case GEMINI_OAUTH -> gatewayProperties.getOauth().getGeminiAuthBaseUrl();
            case CLAUDE_ACCOUNT -> gatewayProperties.getOauth().getClaudeAuthBaseUrl();
        };
    }

    private String resolveClientId(UpstreamAccountProviderType providerType) {
        return switch (providerType) {
            case OPENAI_OAUTH -> gatewayProperties.getOauth().getOpenaiClientId();
            case GEMINI_OAUTH -> gatewayProperties.getOauth().getGeminiClientId();
            case CLAUDE_ACCOUNT -> gatewayProperties.getOauth().getClaudeClientId();
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
