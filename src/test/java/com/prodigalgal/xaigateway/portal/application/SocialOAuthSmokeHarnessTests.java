package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.smoke.SmokeHarnessSupport;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialOAuthSmokeHarnessTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExchangeAllMockProvidersWhenSmokeEnabled() {
        Assumptions.assumeTrue(
                SmokeHarnessSupport.enabled("XAG_SMOKE_OAUTH_MOCK"),
                "设置 XAG_SMOKE_OAUTH_MOCK=true 后才执行 OAuth mock smoke。"
        );
        GatewayProperties properties = configuredProperties();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(mockOAuthExchange());
        List<SocialOAuthProfileClient> clients = List.of(
                new GoogleSocialOAuthProfileClient(properties, builder, objectMapper),
                new GitHubSocialOAuthProfileClient(properties, builder, objectMapper),
                new GenericOAuth2SocialOAuthProfileClient(properties, builder, objectMapper)
        );

        Map<SocialOAuthProvider, String> expectedSubjects = Map.of(
                SocialOAuthProvider.GOOGLE, "google:google-smoke-7",
                SocialOAuthProvider.GITHUB, "github:42",
                SocialOAuthProvider.QQ, "qq:qq-open-smoke",
                SocialOAuthProvider.WECHAT, "wechat:wechat-union-smoke",
                SocialOAuthProvider.META, "meta:meta-smoke-7",
                SocialOAuthProvider.X, "x:x-smoke-7"
        );

        StringBuilder report = new StringBuilder();
        for (SocialOAuthProvider provider : SocialOAuthProvider.values()) {
            SocialOAuthProfileClient client = clients.stream()
                    .filter(candidate -> candidate.supports(provider))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("未找到 mock provider client：" + provider));
            SocialOAuthProfile profile = client.exchange(exchangeRequest(provider));

            assertEquals(expectedSubjects.get(provider), profile.externalSubject());
            assertNotNull(profile.displayName(), provider + " displayName");
            report.append("- ")
                    .append(provider.wireName())
                    .append(": ")
                    .append(profile.externalSubject())
                    .append("\n");
        }
        SmokeHarnessSupport.writeReport("social-oauth-mock", report.toString());
    }

    private GatewayProperties configuredProperties() {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth().setGoogleSocialClientId("google-client");
        properties.getOauth().setGoogleSocialClientSecret("google-secret");
        properties.getOauth().setGoogleSocialTokenEndpoint("https://oauth.smoke/google/token");
        properties.getOauth().setGoogleSocialUserInfoEndpoint("https://oauth.smoke/google/userinfo");
        properties.getOauth().setGithubSocialClientId("github-client");
        properties.getOauth().setGithubSocialClientSecret("github-secret");
        properties.getOauth().setGithubSocialTokenEndpoint("https://oauth.smoke/github/token");
        properties.getOauth().setGithubSocialUserEndpoint("https://oauth.smoke/github/user");
        properties.getOauth().setGithubSocialEmailsEndpoint("https://oauth.smoke/github/emails");
        properties.getOauth().setQqSocialClientId("qq-client");
        properties.getOauth().setQqSocialClientSecret("qq-secret");
        properties.getOauth().setQqSocialTokenEndpoint("https://oauth.smoke/qq/token");
        properties.getOauth().setQqSocialOpenIdEndpoint("https://oauth.smoke/qq/openid");
        properties.getOauth().setQqSocialUserInfoEndpoint("https://oauth.smoke/qq/userinfo");
        properties.getOauth().setWechatSocialClientId("wechat-client");
        properties.getOauth().setWechatSocialClientSecret("wechat-secret");
        properties.getOauth().setWechatSocialTokenEndpoint("https://oauth.smoke/wechat/token");
        properties.getOauth().setWechatSocialUserInfoEndpoint("https://oauth.smoke/wechat/userinfo");
        properties.getOauth().setMetaSocialClientId("meta-client");
        properties.getOauth().setMetaSocialClientSecret("meta-secret");
        properties.getOauth().setMetaSocialTokenEndpoint("https://oauth.smoke/meta/token");
        properties.getOauth().setMetaSocialUserInfoEndpoint("https://oauth.smoke/meta/me?fields=id,name,email");
        properties.getOauth().setXSocialClientId("x-client");
        properties.getOauth().setXSocialTokenEndpoint("https://oauth.smoke/x/token");
        properties.getOauth().setXSocialUserInfoEndpoint("https://oauth.smoke/x/users/me?user.fields=id,name,username");
        return properties;
    }

    private ExchangeFunction mockOAuthExchange() {
        return request -> {
            String url = request.url().toString();
            if (url.equals("https://oauth.smoke/google/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"google-token\"}"));
            }
            if (url.equals("https://oauth.smoke/google/userinfo")) {
                assertEquals("Bearer google-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("{\"sub\":\"google-smoke-7\",\"email\":\"google-smoke@example.com\",\"name\":\"Google Smoke\"}"));
            }
            if (url.equals("https://oauth.smoke/github/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"github-token\"}"));
            }
            if (url.equals("https://oauth.smoke/github/user")) {
                assertEquals("Bearer github-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("{\"id\":42,\"login\":\"octo-smoke\",\"name\":\"GitHub Smoke\",\"email\":\"github-smoke@example.com\"}"));
            }
            if (url.equals("https://oauth.smoke/github/emails")) {
                assertEquals("Bearer github-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("[{\"email\":\"github-primary-smoke@example.com\",\"primary\":true,\"verified\":true}]"));
            }
            if (url.equals("https://oauth.smoke/qq/token")) {
                return Mono.just(textResponse("access_token=qq-token&expires_in=7200"));
            }
            if (url.startsWith("https://oauth.smoke/qq/openid")) {
                assertTrue(url.contains("access_token=qq-token"));
                return Mono.just(textResponse("callback( {\"openid\":\"qq-open-smoke\"} );"));
            }
            if (url.startsWith("https://oauth.smoke/qq/userinfo")) {
                assertTrue(url.contains("openid=qq-open-smoke"));
                return Mono.just(jsonResponse("{\"ret\":0,\"nickname\":\"QQ Smoke\"}"));
            }
            if (url.equals("https://oauth.smoke/wechat/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"wechat-token\",\"openid\":\"wechat-open-smoke\"}"));
            }
            if (url.startsWith("https://oauth.smoke/wechat/userinfo")) {
                assertTrue(url.contains("openid=wechat-open-smoke"));
                return Mono.just(jsonResponse("{\"openid\":\"wechat-open-smoke\",\"unionid\":\"wechat-union-smoke\",\"nickname\":\"WeChat Smoke\"}"));
            }
            if (url.equals("https://oauth.smoke/meta/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"meta-token\"}"));
            }
            if (url.equals("https://oauth.smoke/meta/me?fields=id,name,email")) {
                assertEquals("Bearer meta-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("{\"id\":\"meta-smoke-7\",\"name\":\"Meta Smoke\",\"email\":\"meta-smoke@example.com\"}"));
            }
            if (url.equals("https://oauth.smoke/x/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"x-token\"}"));
            }
            if (url.equals("https://oauth.smoke/x/users/me?user.fields=id,name,username")) {
                assertEquals("Bearer x-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("{\"data\":{\"id\":\"x-smoke-7\",\"username\":\"xsmoke\",\"name\":\"X Smoke\"}}"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
    }

    private SocialOAuthTokenExchangeRequest exchangeRequest(SocialOAuthProvider provider) {
        return new SocialOAuthTokenExchangeRequest(
                provider,
                "code-" + provider.wireName(),
                "state-" + provider.wireName(),
                "https://gateway.smoke/portal/auth/oauth/" + provider.wireName() + "/callback",
                "verifier-" + provider.wireName(),
                null,
                null,
                null,
                null
        );
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private ClientResponse textResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(body)
                .build();
    }
}
