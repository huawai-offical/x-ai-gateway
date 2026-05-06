package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialOAuthProfileClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExchangeGoogleCodeAndVerifyIdTokenWithJwks() throws Exception {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth().setGoogleSocialClientId("google-client");
        properties.getOauth().setGoogleSocialClientSecret("google-secret");
        properties.getOauth().setGoogleSocialTokenEndpoint("https://oauth.test/google/token");
        properties.getOauth().setGoogleSocialJwksUri("https://oauth.test/google/jwks");
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String idToken = signedGoogleIdToken(keyPair, "google-client", "kid-1");
        String jwks = jwks(keyPair, "kid-1");
        AtomicInteger jwksRequests = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.equals("https://oauth.test/google/token")) {
                return Mono.just(jsonResponse("{\"id_token\":\"" + idToken + "\"}"));
            }
            if (url.equals("https://oauth.test/google/jwks")) {
                jwksRequests.incrementAndGet();
                return Mono.just(jsonResponse(jwks));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
        GoogleSocialOAuthProfileClient client = new GoogleSocialOAuthProfileClient(
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper
        );

        SocialOAuthProfile profile = client.exchange(new SocialOAuthTokenExchangeRequest(
                SocialOAuthProvider.GOOGLE,
                "code-1",
                "state-1",
                "https://gateway.example.com/callback",
                "verifier-1",
                null,
                null,
                null,
                null
        ));

        assertTrue(client.supports(SocialOAuthProvider.GOOGLE));
        assertEquals("google:google-sub-7", profile.externalSubject());
        assertEquals("google-user@example.com", profile.email());
        assertEquals("Google User", profile.displayName());

        client.exchange(new SocialOAuthTokenExchangeRequest(
                SocialOAuthProvider.GOOGLE,
                "code-2",
                "state-2",
                "https://gateway.example.com/callback",
                "verifier-2",
                null,
                null,
                null,
                null
        ));
        assertEquals(1, jwksRequests.get());
    }

    @Test
    void shouldExchangeGithubCodeAndPreferVerifiedPrimaryEmail() {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth().setGithubSocialClientId("github-client");
        properties.getOauth().setGithubSocialClientSecret("github-secret");
        properties.getOauth().setGithubSocialTokenEndpoint("https://oauth.test/github/token");
        properties.getOauth().setGithubSocialUserEndpoint("https://oauth.test/github/user");
        properties.getOauth().setGithubSocialEmailsEndpoint("https://oauth.test/github/emails");
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.equals("https://oauth.test/github/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"gh-token\"}"));
            }
            if (url.equals("https://oauth.test/github/user")) {
                assertEquals("Bearer gh-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("{\"id\":42,\"login\":\"octo\",\"name\":\"Octo User\",\"email\":\"public@example.com\"}"));
            }
            if (url.equals("https://oauth.test/github/emails")) {
                assertEquals("Bearer gh-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("[{\"email\":\"primary@example.com\",\"primary\":true,\"verified\":true}]"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
        GitHubSocialOAuthProfileClient client = new GitHubSocialOAuthProfileClient(
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper
        );

        SocialOAuthProfile profile = client.exchange(new SocialOAuthTokenExchangeRequest(
                SocialOAuthProvider.GITHUB,
                "code-1",
                "state-1",
                "https://gateway.example.com/callback",
                null,
                null,
                null,
                null,
                null
        ));

        assertTrue(client.supports(SocialOAuthProvider.GITHUB));
        assertEquals("github:42", profile.externalSubject());
        assertEquals("primary@example.com", profile.email());
        assertEquals("Octo User", profile.displayName());
    }

    @Test
    void shouldExchangeQqCodeWithOpenIdAndUserInfo() {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth().setQqSocialClientId("qq-client");
        properties.getOauth().setQqSocialClientSecret("qq-secret");
        properties.getOauth().setQqSocialTokenEndpoint("https://oauth.test/qq/token");
        properties.getOauth().setQqSocialOpenIdEndpoint("https://oauth.test/qq/openid");
        properties.getOauth().setQqSocialUserInfoEndpoint("https://oauth.test/qq/userinfo");
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.equals("https://oauth.test/qq/token")) {
                return Mono.just(textResponse("access_token=qq-token&expires_in=7200"));
            }
            if (url.startsWith("https://oauth.test/qq/openid")) {
                assertTrue(url.contains("access_token=qq-token"));
                return Mono.just(textResponse("callback( {\"openid\":\"qq-open-7\"} );"));
            }
            if (url.startsWith("https://oauth.test/qq/userinfo")) {
                assertTrue(url.contains("openid=qq-open-7"));
                return Mono.just(jsonResponse("{\"ret\":0,\"nickname\":\"QQ User\"}"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
        GenericOAuth2SocialOAuthProfileClient client = new GenericOAuth2SocialOAuthProfileClient(
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper
        );

        SocialOAuthProfile profile = client.exchange(exchangeRequest(SocialOAuthProvider.QQ));

        assertTrue(client.supports(SocialOAuthProvider.QQ));
        assertEquals("qq:qq-open-7", profile.externalSubject());
        assertEquals("QQ User", profile.displayName());
    }

    @Test
    void shouldExchangeWechatCodeAndPreferUnionId() {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth().setWechatSocialClientId("wechat-client");
        properties.getOauth().setWechatSocialClientSecret("wechat-secret");
        properties.getOauth().setWechatSocialTokenEndpoint("https://oauth.test/wechat/token");
        properties.getOauth().setWechatSocialUserInfoEndpoint("https://oauth.test/wechat/userinfo");
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.equals("https://oauth.test/wechat/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"wx-token\",\"openid\":\"wx-open-7\"}"));
            }
            if (url.startsWith("https://oauth.test/wechat/userinfo")) {
                assertTrue(url.contains("openid=wx-open-7"));
                return Mono.just(jsonResponse("{\"openid\":\"wx-open-7\",\"unionid\":\"wx-union-7\",\"nickname\":\"WeChat User\"}"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
        GenericOAuth2SocialOAuthProfileClient client = new GenericOAuth2SocialOAuthProfileClient(
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper
        );

        SocialOAuthProfile profile = client.exchange(exchangeRequest(SocialOAuthProvider.WECHAT));

        assertTrue(client.supports(SocialOAuthProvider.WECHAT));
        assertEquals("wechat:wx-union-7", profile.externalSubject());
        assertEquals("WeChat User", profile.displayName());
    }

    @Test
    void shouldExchangeMetaCodeWithBearerUserInfo() {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth().setMetaSocialClientId("meta-client");
        properties.getOauth().setMetaSocialClientSecret("meta-secret");
        properties.getOauth().setMetaSocialTokenEndpoint("https://oauth.test/meta/token");
        properties.getOauth().setMetaSocialUserInfoEndpoint("https://oauth.test/meta/me?fields=id,name,email");
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.equals("https://oauth.test/meta/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"meta-token\"}"));
            }
            if (url.equals("https://oauth.test/meta/me?fields=id,name,email")) {
                assertEquals("Bearer meta-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("{\"id\":\"meta-7\",\"name\":\"Meta User\",\"email\":\"meta@example.com\"}"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
        GenericOAuth2SocialOAuthProfileClient client = new GenericOAuth2SocialOAuthProfileClient(
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper
        );

        SocialOAuthProfile profile = client.exchange(exchangeRequest(SocialOAuthProvider.META));

        assertTrue(client.supports(SocialOAuthProvider.META));
        assertEquals("meta:meta-7", profile.externalSubject());
        assertEquals("meta@example.com", profile.email());
        assertEquals("Meta User", profile.displayName());
    }

    @Test
    void shouldExchangeXCodeWithPkceProfile() {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth().setXSocialClientId("x-client");
        properties.getOauth().setXSocialTokenEndpoint("https://oauth.test/x/token");
        properties.getOauth().setXSocialUserInfoEndpoint("https://oauth.test/x/users/me?user.fields=id,name,username");
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.equals("https://oauth.test/x/token")) {
                return Mono.just(jsonResponse("{\"access_token\":\"x-token\"}"));
            }
            if (url.equals("https://oauth.test/x/users/me?user.fields=id,name,username")) {
                assertEquals("Bearer x-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(jsonResponse("{\"data\":{\"id\":\"x-7\",\"username\":\"xuser\",\"name\":\"X User\"}}"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
        };
        GenericOAuth2SocialOAuthProfileClient client = new GenericOAuth2SocialOAuthProfileClient(
                properties,
                WebClient.builder().exchangeFunction(exchangeFunction),
                objectMapper
        );

        SocialOAuthProfile profile = client.exchange(exchangeRequest(SocialOAuthProvider.X));

        assertTrue(client.supports(SocialOAuthProvider.X));
        assertEquals("x:x-7", profile.externalSubject());
        assertEquals("X User", profile.displayName());
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

    private SocialOAuthTokenExchangeRequest exchangeRequest(SocialOAuthProvider provider) {
        return new SocialOAuthTokenExchangeRequest(
                provider,
                "code-1",
                "state-1",
                "https://gateway.example.com/callback",
                "verifier-1",
                null,
                null,
                null,
                null
        );
    }

    private String signedGoogleIdToken(KeyPair keyPair, String audience, String kid) throws Exception {
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        String header = "{\"alg\":\"RS256\",\"kid\":\"" + kid + "\",\"typ\":\"JWT\"}";
        String payload = "{\"iss\":\"https://accounts.google.com\",\"aud\":\"" + audience
                + "\",\"sub\":\"google-sub-7\",\"email\":\"google-user@example.com\",\"email_verified\":true,"
                + "\"name\":\"Google User\",\"exp\":" + expiresAt + "}";
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8))
                + "."
                + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(signature.sign());
    }

    private String jwks(KeyPair keyPair, String kid) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"alg\":\"RS256\",\"use\":\"sig\","
                + "\"n\":\"" + base64Url(publicKey.getModulus()) + "\","
                + "\"e\":\"" + base64Url(publicKey.getPublicExponent()) + "\"}]}";
    }

    private String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return base64Url(bytes);
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
