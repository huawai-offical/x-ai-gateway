package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

abstract class DefaultOAuthSessionRefreshAdapter implements OAuthSessionRefreshAdapter {

    private final UpstreamAccountProviderType providerType;
    private final String adapterName;
    private final String providerHeader;
    private final int defaultTtlSeconds;

    DefaultOAuthSessionRefreshAdapter(
            UpstreamAccountProviderType providerType,
            String adapterName,
            String providerHeader,
            int defaultTtlSeconds) {
        this.providerType = providerType;
        this.adapterName = adapterName;
        this.providerHeader = providerHeader;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    @Override
    public UpstreamAccountProviderType providerType() {
        return providerType;
    }

    @Override
    public OAuthSessionRefreshResult refresh(OAuthSessionRefreshRequest request) {
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new IllegalStateException("缺少 refresh token，无法刷新 OAuth / Session 账号。");
        }
        Instant tokenExpiresAt = request.now().plusSeconds(defaultTtlSeconds);
        return new OAuthSessionRefreshResult(
                adapterName,
                request.accessToken(),
                request.refreshToken(),
                tokenExpiresAt,
                tokenExpiresAt.minusSeconds(Math.min(300, defaultTtlSeconds / 4)),
                request.now(),
                3600,
                null,
                null,
                Map.of(
                        "authorization", "Bearer ***",
                        providerHeader, request.accountName() == null ? "unknown" : request.accountName(),
                        "x-refresh-adapter", adapterName
                ),
                Map.of(
                        "mode", "local_snapshot",
                        "providerType", request.providerType().name()
                )
        );
    }
}

@Component
class OpenAiOAuthSessionRefreshAdapter extends DefaultOAuthSessionRefreshAdapter {
    OpenAiOAuthSessionRefreshAdapter() {
        super(UpstreamAccountProviderType.OPENAI_OAUTH, "openai-oauth-session", "x-openai-account", 3600);
    }
}

@Component
class GeminiOAuthSessionRefreshAdapter extends DefaultOAuthSessionRefreshAdapter {
    GeminiOAuthSessionRefreshAdapter() {
        super(UpstreamAccountProviderType.GEMINI_OAUTH, "gemini-oauth-session", "x-goog-user-project", 3600);
    }
}

@Component
class ClaudeOAuthSessionRefreshAdapter extends DefaultOAuthSessionRefreshAdapter {
    ClaudeOAuthSessionRefreshAdapter() {
        super(UpstreamAccountProviderType.CLAUDE_ACCOUNT, "claude-session", "x-claude-account", 1800);
    }
}
