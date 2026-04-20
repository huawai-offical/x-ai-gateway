package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.admin.application.AdminAuthService;
import com.prodigalgal.xaigateway.admin.application.SystemSettingsAdminService;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import com.prodigalgal.xaigateway.infra.config.web.AdminConsoleSecurityConfiguration;
import com.prodigalgal.xaigateway.infra.config.web.TraceIdWebFilter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {AdminAuthController.class, SystemSettingsAdminController.class})
@Import({
        AdminAuthService.class,
        AdminConsoleSecurityConfiguration.class,
        TraceIdWebFilter.class,
        AdminAuthControllerTests.AdminAuthTestConfig.class,
})
class AdminAuthControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SystemSettingsAdminService systemSettingsAdminService;

    @BeforeEach
    void setUp() {
        when(systemSettingsAdminService.get()).thenReturn(new SystemSettingsResponse(
                new SystemSettingsResponse.UpstreamCacheSettingsResponse(
                        true,
                        true,
                        true,
                        true,
                        "PT30M",
                        1024,
                        "xag"
                ),
                new SystemSettingsResponse.UpstreamRuntimeSettingsResponse(
                        180000,
                        600000,
                        180000,
                        600000
                ),
                Instant.parse("2026-04-20T08:00:00Z")
        ));
    }

    @Test
    void shouldRejectProtectedAdminApiWithoutSession() {
        webTestClient.get()
                .uri("/admin/settings")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void shouldIssueChallengeAuthenticateAndLogout() throws Exception {
        EntityExchangeResult<AdminAuthChallengeResponse> challengeResult = webTestClient.post()
                .uri("/admin/auth/challenge")
                .exchange()
                .expectStatus().isOk()
                .expectBody(AdminAuthChallengeResponse.class)
                .returnResult()
                ;

        AdminAuthChallengeResponse challenge = challengeResult.getResponseBody();
        String challengeSessionCookie = challengeResult.getResponseCookies().getFirst("SESSION").getValue();

        String powNonce = solvePow(challenge.challengeId(), challenge.powSalt(), challenge.powDifficulty());
        int mathAnswer = solveMath(challenge.mathPrompt());

        EntityExchangeResult<AdminSessionResponse> loginResult = webTestClient.mutate()
                .defaultCookie("SESSION", challengeSessionCookie)
                .build()
                .post()
                .uri("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username":"console-admin",
                          "password":"secret-123",
                          "challengeId":"%s",
                          "mathAnswer":%d,
                          "powNonce":"%s"
                        }
                        """.formatted(challenge.challengeId(), mathAnswer, powNonce))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AdminSessionResponse.class)
                .returnResult();

        String sessionCookie = loginResult.getResponseCookies().getFirst("SESSION").getValue();
        WebTestClient authenticatedClient = webTestClient.mutate()
                .defaultCookie("SESSION", sessionCookie)
                .build();

        authenticatedClient.get()
                .uri("/admin/auth/session")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authenticated").isEqualTo(true)
                .jsonPath("$.username").isEqualTo("console-admin");

        authenticatedClient.get()
                .uri("/admin/settings")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.upstreamCache.keyPrefix").isEqualTo("xag");

        authenticatedClient.post()
                .uri("/admin/auth/logout")
                .exchange()
                .expectStatus().isOk();

        authenticatedClient.get()
                .uri("/admin/settings")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectLoginWhenPowIsInvalid() {
        EntityExchangeResult<AdminAuthChallengeResponse> challengeResult = webTestClient.post()
                .uri("/admin/auth/challenge")
                .exchange()
                .expectStatus().isOk()
                .expectBody(AdminAuthChallengeResponse.class)
                .returnResult()
                ;

        AdminAuthChallengeResponse challenge = challengeResult.getResponseBody();
        String challengeSessionCookie = challengeResult.getResponseCookies().getFirst("SESSION").getValue();

        webTestClient.mutate()
                .defaultCookie("SESSION", challengeSessionCookie)
                .build()
                .post()
                .uri("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username":"console-admin",
                          "password":"secret-123",
                          "challengeId":"%s",
                          "mathAnswer":%d,
                          "powNonce":"invalid"
                        }
                        """.formatted(challenge.challengeId(), solveMath(challenge.mathPrompt())))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_ARGUMENT");
    }

    private static int solveMath(String prompt) {
        String[] tokens = prompt.replace("= ?", "").trim().split(" ");
        int first = Integer.parseInt(tokens[0]);
        int second = Integer.parseInt(tokens[2]);
        return switch (tokens[1]) {
            case "+" -> first + second;
            case "-" -> first - second;
            case "*" -> first * second;
            default -> throw new IllegalArgumentException("未知数学验证码：" + prompt);
        };
    }

    private static String solvePow(String challengeId, String powSalt, int difficulty) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String target = "0".repeat(Math.max(0, difficulty));
        for (int attempt = 0; attempt < 200_000; attempt += 1) {
            String nonce = Integer.toHexString(attempt);
            byte[] hash = digest.digest((challengeId + ":" + powSalt + ":" + nonce)
                    .getBytes(StandardCharsets.UTF_8));
            String hex = toHex(hash);
            if (hex.startsWith(target)) {
                return nonce;
            }
        }
        throw new IllegalStateException("未在预期尝试次数内求出 POW。");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(Character.forDigit((current >> 4) & 0x0F, 16));
            builder.append(Character.forDigit(current & 0x0F, 16));
        }
        return builder.toString();
    }

    @TestConfiguration
    static class AdminAuthTestConfig {

        @Bean
        GatewayProperties gatewayProperties() {
            GatewayProperties properties = new GatewayProperties();
            properties.getAdminConsole().setEnabled(true);
            properties.getAdminConsole().setUsername("console-admin");
            properties.getAdminConsole().setPassword("{noop}secret-123");
            properties.getAdminConsole().setPowDifficulty(1);
            properties.getAdminConsole().setMathMin(2);
            properties.getAdminConsole().setMathMax(9);
            return properties;
        }
    }
}
