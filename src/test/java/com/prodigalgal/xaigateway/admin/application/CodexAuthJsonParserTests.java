package com.prodigalgal.xaigateway.admin.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexAuthJsonParserTests {

    private final CodexAuthJsonParser parser = new CodexAuthJsonParser(new ObjectMapper());

    @Test
    void shouldParseCodexAuthJsonWithoutLeakingSecretInSummary() {
        var parsed = parser.parse("""
                {
                  "auth_mode": "login",
                  "OPENAI_API_KEY": "sk-local-fallback",
                  "tokens": {
                    "id_token": "id-token-secret",
                    "access_token": "codex-access-secret",
                    "refresh_token": "codex-refresh-secret",
                    "account_id": "acct_123"
                  },
                  "last_refresh": "2026-05-07T01:00:00Z"
                }
                """);

        assertEquals("codex-access-secret", parsed.accessToken());
        assertEquals("codex-refresh-secret", parsed.refreshToken());
        assertEquals("acct_123", parsed.accountId());
        assertEquals("login", parsed.authMode());
        assertEquals("account_id", parsed.identitySource());
        assertEquals("STRONG", parsed.identityStrength());
        assertTrue(parsed.identityKey().startsWith("codex:account:"));
        assertTrue((Boolean) parsed.safeSummary().get("hasAccessToken"));
        assertTrue((Boolean) parsed.safeSummary().get("hasRefreshToken"));
        assertNotNull(parsed.safeSummary().get("accessTokenFingerprint"));
        String summary = parsed.safeSummary().toString();
        assertFalse(summary.contains("codex-access-secret"));
        assertFalse(summary.contains("codex-refresh-secret"));
        assertFalse(summary.contains("id-token-secret"));
    }

    @Test
    void shouldRejectAuthJsonWithoutAccessToken() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {
                  "auth_mode": "login",
                  "tokens": {
                    "refresh_token": "codex-refresh-secret"
                  }
                }
                """));
    }

    @Test
    void shouldPreferJwtSubjectAndEmailAsStableIdentitySignals() {
        var parsed = parser.parse("""
                {
                  "auth_mode": "login",
                  "tokens": {
                    "id_token": "%s",
                    "access_token": "codex-access-secret-v2",
                    "refresh_token": "codex-refresh-secret-v2",
                    "account_id": "local-account-id-can-change"
                  },
                  "profile": {
                    "email": "codex-test@example.com"
                  }
                }
                """.formatted(jwt("""
                {"sub":"stable-openid-subject","email":"codex-test@example.com"}
                """)));

        assertEquals("stable-openid-subject", parsed.identitySubject());
        assertEquals("codex-test@example.com", parsed.email());
        assertEquals("subject", parsed.identitySource());
        assertEquals("STRONG", parsed.identityStrength());
        assertTrue(parsed.identityKey().startsWith("codex:subject:"));
        String summary = parsed.safeSummary().toString();
        assertFalse(summary.contains("codex-test@example.com"));
        assertFalse(summary.contains("stable-openid-subject"));
        assertTrue(summary.contains("subjectFingerprint"));
        assertTrue(summary.contains("emailFingerprint"));
    }

    private String jwt(String payloadJson) {
        return base64Url("{}") + "." + base64Url(payloadJson) + ".sig";
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
