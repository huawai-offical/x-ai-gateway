package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GenericOAuth2SocialOAuthProfileClient implements SocialOAuthProfileClient {

    private static final Set<SocialOAuthProvider> SUPPORTED_PROVIDERS = Set.of(
            SocialOAuthProvider.QQ,
            SocialOAuthProvider.WECHAT,
            SocialOAuthProvider.META,
            SocialOAuthProvider.X
    );

    private final GatewayProperties gatewayProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GenericOAuth2SocialOAuthProfileClient(
            GatewayProperties gatewayProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.gatewayProperties = gatewayProperties;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(SocialOAuthProvider provider) {
        return SUPPORTED_PROVIDERS.contains(provider);
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public SocialOAuthProfile exchange(SocialOAuthTokenExchangeRequest request) {
        if (!supports(request.provider())) {
            throw new IllegalStateException("未配置社交 OAuth provider：" + request.provider().wireName());
        }
        JsonNode token = exchangeToken(request);
        return switch (request.provider()) {
            case QQ -> qqProfile(token, request);
            case WECHAT -> wechatProfile(token, request);
            case META -> metaProfile(token, request);
            case X -> xProfile(token, request);
            default -> throw new IllegalArgumentException("不支持的社交 OAuth provider：" + request.provider().wireName());
        };
    }

    private JsonNode exchangeToken(SocialOAuthTokenExchangeRequest request) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", firstConfigured(request.clientId(), clientId(request.provider())));
        String secret = firstConfigured(request.clientSecret(), clientSecret(request.provider()));
        if (configured(secret)) {
            formData.add("client_secret", secret);
        }
        formData.add("code", required(request.code(), "OAuth authorization code"));
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", request.redirectUri());
        if (configured(request.codeVerifier())) {
            formData.add("code_verifier", request.codeVerifier());
        }
        String payload = webClient.post()
                .uri(URI.create(firstConfigured(request.tokenEndpoint(), tokenEndpoint(request.provider()))))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parsePayload(payload);
    }

    private SocialOAuthProfile qqProfile(JsonNode token, SocialOAuthTokenExchangeRequest request) {
        String accessToken = required(text(token, "access_token"), "QQ access_token");
        String openId = text(token, "openid");
        if (openId == null) {
            JsonNode openIdPayload = get(
                    gatewayProperties.getOauth().getQqSocialOpenIdEndpoint(),
                    Map.of("access_token", accessToken),
                    null
            );
            openId = text(openIdPayload, "openid");
        }
        openId = required(openId, "QQ openid");
        JsonNode user = get(
                firstConfigured(request.userInfoEndpoint(), gatewayProperties.getOauth().getQqSocialUserInfoEndpoint()),
                Map.of(
                        "access_token", accessToken,
                        "oauth_consumer_key", firstConfigured(request.clientId(), gatewayProperties.getOauth().getQqSocialClientId()),
                        "openid", openId
                ),
                null
        );
        return new SocialOAuthProfile(
                SocialOAuthProvider.QQ,
                "qq:" + openId,
                null,
                firstText(user, "nickname", "name"),
                metadata(Map.of("provider", "qq", "source", "qq-api", "openid", openId))
        );
    }

    private SocialOAuthProfile wechatProfile(JsonNode token, SocialOAuthTokenExchangeRequest request) {
        String accessToken = required(text(token, "access_token"), "WeChat access_token");
        String openId = required(text(token, "openid"), "WeChat openid");
        JsonNode user = get(
                firstConfigured(request.userInfoEndpoint(), gatewayProperties.getOauth().getWechatSocialUserInfoEndpoint()),
                Map.of("access_token", accessToken, "openid", openId, "lang", "zh_CN"),
                null
        );
        String unionId = firstText(user, "unionid", "unionId", "UnionId");
        String subject = unionId == null ? openId : unionId;
        return new SocialOAuthProfile(
                SocialOAuthProvider.WECHAT,
                "wechat:" + subject,
                null,
                firstText(user, "nickname", "name"),
                metadata(nonBlankMap(
                        "provider", "wechat",
                        "source", "wechat-api",
                        "openid", openId,
                        "unionid", unionId
                ))
        );
    }

    private SocialOAuthProfile metaProfile(JsonNode token, SocialOAuthTokenExchangeRequest request) {
        String accessToken = required(text(token, "access_token"), "Meta access_token");
        JsonNode user = get(firstConfigured(request.userInfoEndpoint(), gatewayProperties.getOauth().getMetaSocialUserInfoEndpoint()), Map.of(), accessToken);
        String id = required(text(user, "id"), "Meta 用户标识");
        return new SocialOAuthProfile(
                SocialOAuthProvider.META,
                "meta:" + id,
                text(user, "email"),
                firstText(user, "name", "email"),
                metadata(Map.of("provider", "meta", "source", "meta-api"))
        );
    }

    private SocialOAuthProfile xProfile(JsonNode token, SocialOAuthTokenExchangeRequest request) {
        String accessToken = required(text(token, "access_token"), "X access_token");
        JsonNode payload = get(firstConfigured(request.userInfoEndpoint(), gatewayProperties.getOauth().getXSocialUserInfoEndpoint()), Map.of(), accessToken);
        JsonNode user = payload.path("data");
        if (user == null || user.isMissingNode() || user.isNull()) {
            user = payload;
        }
        String id = required(text(user, "id"), "X 用户标识");
        String username = text(user, "username");
        return new SocialOAuthProfile(
                SocialOAuthProvider.X,
                "x:" + id,
                null,
                firstNonBlank(text(user, "name"), username),
                metadata(nonBlankMap("provider", "x", "source", "x-api", "username", username))
        );
    }

    private JsonNode get(String endpoint, Map<String, String> query, String bearerToken) {
        var spec = webClient.get()
                .uri(URI.create(appendQuery(endpoint, query)))
                .accept(MediaType.APPLICATION_JSON);
        if (configured(bearerToken)) {
            spec.headers(headers -> headers.setBearerAuth(bearerToken));
        }
        return parsePayload(spec.retrieve().bodyToMono(String.class).block());
    }

    private JsonNode parsePayload(String payload) {
        String normalized = payload == null ? "" : payload.trim();
        if (normalized.startsWith("callback(") && normalized.endsWith(");")) {
            normalized = normalized.substring("callback(".length(), normalized.length() - 2).trim();
        }
        if (normalized.startsWith("{") || normalized.startsWith("[")) {
            try {
                return objectMapper.readTree(normalized);
            } catch (Exception exception) {
                throw new IllegalArgumentException("OAuth provider 返回了非法 JSON。", exception);
            }
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        for (String part : normalized.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            String[] pair = part.split("=", 2);
            String key = decode(pair[0]);
            String value = pair.length > 1 ? decode(pair[1]) : "";
            node.put(key, value);
        }
        return node;
    }

    private String appendQuery(String endpoint, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return endpoint;
        }
        StringBuilder builder = new StringBuilder(endpoint);
        builder.append(endpoint.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!configured(entry.getValue())) {
                continue;
            }
            if (!first) {
                builder.append("&");
            }
            builder.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
            first = false;
        }
        return builder.toString();
    }

    private Map<String, String> nonBlankMap(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (configured(pairs[index + 1])) {
                values.put(pairs[index], pairs[index + 1]);
            }
        }
        return values;
    }

    private String clientId(SocialOAuthProvider provider) {
        return switch (provider) {
            case QQ -> gatewayProperties.getOauth().getQqSocialClientId();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialClientId();
            case META -> gatewayProperties.getOauth().getMetaSocialClientId();
            case X -> gatewayProperties.getOauth().getXSocialClientId();
            default -> null;
        };
    }

    private String clientSecret(SocialOAuthProvider provider) {
        return switch (provider) {
            case QQ -> gatewayProperties.getOauth().getQqSocialClientSecret();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialClientSecret();
            case META -> gatewayProperties.getOauth().getMetaSocialClientSecret();
            case X -> gatewayProperties.getOauth().getXSocialClientSecret();
            default -> null;
        };
    }

    private String tokenEndpoint(SocialOAuthProvider provider) {
        return switch (provider) {
            case QQ -> gatewayProperties.getOauth().getQqSocialTokenEndpoint();
            case WECHAT -> gatewayProperties.getOauth().getWechatSocialTokenEndpoint();
            case META -> gatewayProperties.getOauth().getMetaSocialTokenEndpoint();
            case X -> gatewayProperties.getOauth().getXSocialTokenEndpoint();
            default -> null;
        };
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (configured(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return configured(text) ? text : null;
    }

    private String metadata(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String required(String value, String field) {
        if (!configured(value)) {
            throw new IllegalArgumentException(field + "不能为空。");
        }
        return value.trim();
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private String firstConfigured(String primary, String fallback) {
        return configured(primary) ? primary.trim() : fallback;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
