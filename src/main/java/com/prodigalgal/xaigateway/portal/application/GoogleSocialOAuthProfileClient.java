package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleSocialOAuthProfileClient implements SocialOAuthProfileClient {

    private final GatewayProperties gatewayProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SocialOAuthJwksCache jwksCache;

    @Autowired
    public GoogleSocialOAuthProfileClient(
            GatewayProperties gatewayProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            SocialOAuthJwksCache jwksCache) {
        this.gatewayProperties = gatewayProperties;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.jwksCache = jwksCache;
    }

    public GoogleSocialOAuthProfileClient(
            GatewayProperties gatewayProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this(
                gatewayProperties,
                webClientBuilder,
                objectMapper,
                new SocialOAuthJwksCache(webClientBuilder, objectMapper, gatewayProperties)
        );
    }

    @Override
    public boolean supports(SocialOAuthProvider provider) {
        return provider == SocialOAuthProvider.GOOGLE
                && configured(gatewayProperties.getOauth().getGoogleSocialClientId())
                && configured(gatewayProperties.getOauth().getGoogleSocialClientSecret());
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public SocialOAuthProfile exchange(SocialOAuthTokenExchangeRequest request) {
        JsonNode token = webClient.post()
                .uri(URI.create(gatewayProperties.getOauth().getGoogleSocialTokenEndpoint()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters
                        .fromFormData("client_id", gatewayProperties.getOauth().getGoogleSocialClientId())
                        .with("client_secret", gatewayProperties.getOauth().getGoogleSocialClientSecret())
                        .with("code", request.code())
                        .with("grant_type", "authorization_code")
                        .with("redirect_uri", request.redirectUri())
                        .with("code_verifier", request.codeVerifier() == null ? "" : request.codeVerifier()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String idToken = text(token, "id_token");
        if (idToken != null) {
            JsonNode claims = verifyIdToken(idToken);
            return profileFromClaims(claims, "id_token");
        }

        String accessToken = text(token, "access_token");
        if (accessToken == null) {
            throw new IllegalArgumentException("Google OAuth token response 缺少 access_token/id_token。");
        }
        JsonNode userInfo = webClient.get()
                .uri(URI.create(gatewayProperties.getOauth().getGoogleSocialUserInfoEndpoint()))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        return profileFromClaims(userInfo, "userinfo");
    }

    JsonNode verifyIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Google id_token 格式非法。");
            }
            JsonNode header = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8));
            JsonNode claims = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8));
            if (!"RS256".equals(text(header, "alg"))) {
                throw new IllegalArgumentException("Google id_token 仅支持 RS256。");
            }
            PublicKey publicKey = resolvePublicKey(text(header, "kid"));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new IllegalArgumentException("Google id_token 签名验证失败。");
            }
            validateClaims(claims);
            return claims;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Google id_token 验签失败。", exception);
        }
    }

    private PublicKey resolvePublicKey(String kid) throws Exception {
        JsonNode jwks = jwksCache.getJwks(gatewayProperties.getOauth().getGoogleSocialJwksUri());
        PublicKey publicKey = resolvePublicKey(jwks, kid);
        if (publicKey != null) {
            return publicKey;
        }
        publicKey = resolvePublicKey(jwksCache.refresh(gatewayProperties.getOauth().getGoogleSocialJwksUri()), kid);
        if (publicKey != null) {
            return publicKey;
        }
        throw new IllegalArgumentException("Google JWKS 中未找到匹配 kid。");
    }

    private PublicKey resolvePublicKey(JsonNode jwks, String kid) throws Exception {
        JsonNode keys = jwks == null ? null : jwks.path("keys");
        if (keys == null || !keys.isArray()) {
            throw new IllegalArgumentException("Google JWKS 响应缺少 keys。");
        }
        for (JsonNode key : keys) {
            if (kid != null && !kid.equals(text(key, "kid"))) {
                continue;
            }
            JsonNode x5c = key.path("x5c");
            if (x5c.isArray() && !x5c.isEmpty()) {
                byte[] certificate = Base64.getDecoder().decode(x5c.get(0).asText());
                return CertificateFactory.getInstance("X.509")
                        .generateCertificate(new java.io.ByteArrayInputStream(certificate))
                        .getPublicKey();
            }
            String modulus = text(key, "n");
            String exponent = text(key, "e");
            if (modulus != null && exponent != null) {
                RSAPublicKeySpec spec = new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(modulus)),
                        new BigInteger(1, Base64.getUrlDecoder().decode(exponent))
                );
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
                return publicKey;
            }
        }
        return null;
    }

    private void validateClaims(JsonNode claims) {
        String issuer = text(claims, "iss");
        if (!List.of("https://accounts.google.com", "accounts.google.com").contains(issuer)) {
            throw new IllegalArgumentException("Google id_token issuer 非法。");
        }
        if (!gatewayProperties.getOauth().getGoogleSocialClientId().equals(text(claims, "aud"))) {
            throw new IllegalArgumentException("Google id_token audience 非法。");
        }
        long expiresAt = claims.path("exp").asLong(0L);
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Google id_token 已过期。");
        }
        if (!claims.path("email_verified").asBoolean(false)) {
            throw new IllegalArgumentException("Google 邮箱尚未验证。");
        }
        if (text(claims, "sub") == null) {
            throw new IllegalArgumentException("Google id_token 缺少 sub。");
        }
    }

    private SocialOAuthProfile profileFromClaims(JsonNode claims, String source) {
        String subject = required(text(claims, "sub"), "Google 用户标识");
        return new SocialOAuthProfile(
                SocialOAuthProvider.GOOGLE,
                "google:" + subject,
                text(claims, "email"),
                firstText(claims, "name", "given_name", "email"),
                metadata(Map.of("source", source, "provider", "google"))
        );
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String metadata(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空。");
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }
}
