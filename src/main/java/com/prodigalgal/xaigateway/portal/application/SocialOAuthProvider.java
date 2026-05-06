package com.prodigalgal.xaigateway.portal.application;

import java.util.List;
import java.util.Locale;

public enum SocialOAuthProvider {
    GOOGLE("Google", "https://accounts.google.com/o/oauth2/v2/auth", List.of("openid", "email", "profile")),
    QQ("QQ", "https://graph.qq.com/oauth2.0/authorize", List.of("get_user_info")),
    WECHAT("WeChat", "https://open.weixin.qq.com/connect/qrconnect", List.of("snsapi_login")),
    GITHUB("GitHub", "https://github.com/login/oauth/authorize", List.of("read:user", "user:email")),
    META("Meta", "https://www.facebook.com/v20.0/dialog/oauth", List.of("email", "public_profile")),
    X("X", "https://twitter.com/i/oauth2/authorize", List.of("users.read", "tweet.read"));

    private final String displayName;
    private final String authorizationEndpoint;
    private final List<String> defaultScopes;

    SocialOAuthProvider(String displayName, String authorizationEndpoint, List<String> defaultScopes) {
        this.displayName = displayName;
        this.authorizationEndpoint = authorizationEndpoint;
        this.defaultScopes = defaultScopes;
    }

    public String displayName() {
        return displayName;
    }

    public String authorizationEndpoint() {
        return authorizationEndpoint;
    }

    public List<String> defaultScopes() {
        return defaultScopes;
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SocialOAuthProvider fromWireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OAuth provider 不能为空。");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("WEIXIN".equals(normalized)) {
            normalized = "WECHAT";
        }
        if ("FACEBOOK".equals(normalized)) {
            normalized = "META";
        }
        if ("TWITTER".equals(normalized)) {
            normalized = "X";
        }
        return SocialOAuthProvider.valueOf(normalized);
    }
}
