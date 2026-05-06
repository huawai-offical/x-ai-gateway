package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.SystemSettingsResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPolicyServiceTests {

    @Test
    void shouldBlockPrivateUrlAndDetectSensitiveWords() {
        SystemSettingsAdminService settingsService = Mockito.mock(SystemSettingsAdminService.class);
        Mockito.when(settingsService.get()).thenReturn(settings(List.of("api.openai.com", "*.example.com"), List.of("secret", "违规")));
        SecurityPolicyService service = new SecurityPolicyService(settingsService);

        assertTrue(service.scanUrl("https://api.openai.com/v1/chat/completions").allowed());
        assertTrue(service.scanUrl("https://foo.example.com/v1").allowed());
        assertFalse(service.scanUrl("http://127.0.0.1:8080/admin").allowed());
        assertFalse(service.scanUrl("https://evil.test/v1").allowed());
        assertThrows(IllegalArgumentException.class, () -> service.assertUrlAllowed("http://localhost:8080"));

        var text = service.scanText("这里包含 secret token");

        assertFalse(text.allowed());
        assertTrue(text.matchedWords().contains("secret"));
    }

    private SystemSettingsResponse settings(List<String> allowedHosts, List<String> sensitiveWords) {
        return new SystemSettingsResponse(
                new SystemSettingsResponse.UpstreamCacheSettingsResponse(true, true, true, true, "PT30M", 1024, "xag"),
                new SystemSettingsResponse.UpstreamRuntimeSettingsResponse(180000, 600000, 180000, 600000),
                new SystemSettingsResponse.SecuritySettingsResponse(true, false, allowedHosts, sensitiveWords),
                Instant.parse("2026-05-01T08:00:00Z")
        );
    }
}
