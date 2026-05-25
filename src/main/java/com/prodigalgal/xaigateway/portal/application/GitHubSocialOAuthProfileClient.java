package com.prodigalgal.xaigateway.portal.application;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GitHubSocialOAuthProfileClient implements SocialOAuthProfileClient {

    private final GatewayProperties gatewayProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GitHubSocialOAuthProfileClient(
            GatewayProperties gatewayProperties,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.gatewayProperties = gatewayProperties;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(SocialOAuthProvider provider) {
        return provider == SocialOAuthProvider.GITHUB;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public SocialOAuthProfile exchange(SocialOAuthTokenExchangeRequest request) {
        JsonNode token = webClient.post()
                .uri(URI.create(firstConfigured(request.tokenEndpoint(), gatewayProperties.getOauth().getGithubSocialTokenEndpoint())))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters
                        .fromFormData("client_id", firstConfigured(request.clientId(), gatewayProperties.getOauth().getGithubSocialClientId()))
                        .with("client_secret", firstConfigured(request.clientSecret(), gatewayProperties.getOauth().getGithubSocialClientSecret()))
                        .with("code", request.code())
                        .with("redirect_uri", request.redirectUri())
                        .with("code_verifier", request.codeVerifier() == null ? "" : request.codeVerifier()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        String accessToken = required(text(token, "access_token"), "GitHub access_token");
        JsonNode user = webClient.get()
                .uri(URI.create(firstConfigured(request.userInfoEndpoint(), gatewayProperties.getOauth().getGithubSocialUserEndpoint())))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        JsonNode emails = webClient.get()
                .uri(URI.create(gatewayProperties.getOauth().getGithubSocialEmailsEndpoint()))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String id = required(text(user, "id"), "GitHub 用户标识");
        String email = verifiedPrimaryEmail(emails);
        if (email == null) {
            email = text(user, "email");
        }
        String login = text(user, "login");
        return new SocialOAuthProfile(
                SocialOAuthProvider.GITHUB,
                "github:" + id,
                email,
                firstNonBlank(text(user, "name"), login, email),
                metadata(Map.of("source", "github-api", "provider", "github", "login", login == null ? "" : login))
        );
    }

    private String verifiedPrimaryEmail(JsonNode emails) {
        if (emails == null || !emails.isArray()) {
            return null;
        }
        for (JsonNode email : emails) {
            if (email.path("primary").asBoolean(false) && email.path("verified").asBoolean(false)) {
                return text(email, "email");
            }
        }
        for (JsonNode email : emails) {
            if (email.path("verified").asBoolean(false)) {
                return text(email, "email");
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

    private String metadata(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private String firstConfigured(String primary, String fallback) {
        return configured(primary) ? primary.trim() : fallback;
    }
}
