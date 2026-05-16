package com.prodigalgal.xaigateway.provider.adapter.suno;

import com.prodigalgal.xaigateway.smoke.SmokeHarnessSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SunoMusicProviderSmokeHarnessTests {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    @Test
    void shouldRunSunoLikeMusicSmokeWhenExplicitlyEnabled() {
        Assumptions.assumeTrue(
                SmokeHarnessSupport.enabled("XAG_SMOKE_SUNO"),
                "设置 XAG_SMOKE_SUNO=true 后才执行 Suno-like 真实 provider smoke。"
        );
        String baseUrl = SmokeHarnessSupport.env("XAG_SMOKE_SUNO_BASE_URL", "SUNO_BASE_URL", "");
        Assumptions.assumeFalse(
                baseUrl.isBlank(),
                "设置 XAG_SMOKE_SUNO_BASE_URL 或 SUNO_BASE_URL 后才执行 Suno-like 真实 provider smoke。"
        );
        List<String> keys = SmokeHarnessSupport.envList("XAG_SMOKE_SUNO_API_KEY", "SUNO_API_KEY");
        Assumptions.assumeFalse(
                keys.isEmpty(),
                "设置 XAG_SMOKE_SUNO_API_KEY 或 SUNO_API_KEY 后才执行 Suno-like 真实 provider smoke。"
        );

        SmokeResult result = submitOneKey(baseUrl, keys.get(0));
        writeReport(normalizeBaseUrl(baseUrl), result);

        assertTrue(
                result.success(),
                "Suno-like 真实 smoke 未成功；请查看 build/reports/xag-smoke/suno-music.md 的脱敏分类。"
        );
    }

    private SmokeResult submitOneKey(String baseUrl, String apiKey) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String keyRef = SmokeHarnessSupport.secretRef(apiKey);
        try {
            HttpResult submit = post(
                    normalizedBaseUrl + "/suno/submit/MUSIC",
                    apiKey,
                    """
                            {
                              "gpt_description_prompt": "x-ai-gateway smoke, short instrumental theme",
                              "prompt": "short calm instrumental theme",
                              "mv": "chirp-v3-0",
                              "title": "XAG Smoke",
                              "tags": "ambient,synth",
                              "make_instrumental": true
                            }
                            """
            );
            String failureClass = classify(submit.status(), submit.body());
            String taskId = taskId(submit.body());
            String fetchClass = "not_requested";
            if (submit.status() == 200 && taskId != null && SmokeHarnessSupport.enabled("XAG_SMOKE_SUNO_FETCH")) {
                HttpResult fetch = post(
                        normalizedBaseUrl + "/suno/fetch",
                        apiKey,
                        "{\"ids\":[\"" + taskId + "\"],\"task_ids\":[\"" + taskId + "\"]}"
                );
                fetchClass = classify(fetch.status(), fetch.body());
            }
            return new SmokeResult(
                    keyRef,
                    submit.status(),
                    failureClass,
                    fetchClass,
                    taskId != null,
                    taskId == null ? errorMessage(submit.body()) : "taskId=" + taskId
            );
        } catch (RuntimeException exception) {
            return new SmokeResult(keyRef, 0, "NETWORK_ERROR", "not_requested", false, exception.getClass().getSimpleName());
        }
    }

    private HttpResult post(String url, String apiKey, String body) {
        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(responseBody -> new HttpResult(response.statusCode().value(), responseBody)))
                .block(REQUEST_TIMEOUT);
    }

    private String classify(int status, String body) {
        if (status == 200 && taskId(body) != null) {
            return "SUCCESS";
        }
        if (status == 400 || status == 404 || status == 422) {
            return "PARAMETER_UNSUPPORTED";
        }
        if (status == 401 || status == 403) {
            return "AUTHENTICATION_FAILED";
        }
        if (status == 402) {
            return "QUOTA_EXCEEDED";
        }
        if (status == 429) {
            return "PROVIDER_RATE_LIMITED";
        }
        if (status >= 500 || status == 0) {
            return "NETWORK_ERROR";
        }
        return "PARAMETER_UNSUPPORTED";
    }

    private String taskId(String body) {
        JsonNode root = read(body);
        if (root.path("data").isTextual() && !root.path("data").asText().isBlank()) {
            return root.path("data").asText();
        }
        String nested = root.path("data").path("task_id").asText("");
        return nested.isBlank() ? null : nested;
    }

    private String errorMessage(String body) {
        JsonNode root = read(body);
        String message = root.path("message").asText("");
        if (!message.isBlank()) {
            return message;
        }
        message = root.path("error").path("message").asText("");
        return message.isBlank() ? "none" : message;
    }

    private JsonNode read(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void writeReport(String baseUrl, SmokeResult result) {
        String report = "- provider: suno-like\n"
                + "- baseUrl: " + baseUrl + "\n"
                + "- secretPolicy: report uses sha256 prefix references only\n"
                + "- remoteFetch: " + SmokeHarnessSupport.enabled("XAG_SMOKE_SUNO_FETCH") + "\n\n"
                + "| keyRef | submitHttpStatus | submitClass | fetchClass | success | detail |\n"
                + "| --- | --- | --- | --- | --- | --- |\n"
                + "| " + result.keyRef()
                + " | " + result.submitHttpStatus()
                + " | " + result.submitClass()
                + " | " + result.fetchClass()
                + " | " + result.success()
                + " | " + result.detail()
                + " |\n";
        SmokeHarnessSupport.writeReport("suno-music", report);
    }

    private record HttpResult(int status, String body) {
    }

    private record SmokeResult(
            String keyRef,
            int submitHttpStatus,
            String submitClass,
            String fetchClass,
            boolean success,
            String detail
    ) {
    }
}
