package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.prodigalgal.xaigateway.smoke.SmokeHarnessSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiProviderSmokeHarnessTests {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final List<String> PREFERRED_MODELS = List.of(
            "models/gemini-2.5-flash-lite",
            "models/gemini-2.5-flash",
            "models/gemini-2.0-flash-lite",
            "models/gemini-2.0-flash"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    @Test
    void shouldRunGeminiAiStudioSmokeWhenCredentialsAreProvided() {
        Assumptions.assumeTrue(
                SmokeHarnessSupport.enabled("XAG_SMOKE_GEMINI"),
                "设置 XAG_SMOKE_GEMINI=true 后才执行 Gemini 真实 provider smoke。"
        );
        List<String> keys = SmokeHarnessSupport.envList(
                "XAG_SMOKE_GEMINI_API_KEYS",
                "GOOGLE_AI_STUDIO_API_KEYS",
                "GEMINI_API_KEY"
        );
        Assumptions.assumeFalse(
                keys.isEmpty(),
                "设置 XAG_SMOKE_GEMINI_API_KEYS 或 GEMINI_API_KEY 后才执行 Gemini 真实 provider smoke。"
        );

        String baseUrl = SmokeHarnessSupport.env(
                "XAG_SMOKE_GEMINI_BASE_URL",
                null,
                "https://generativelanguage.googleapis.com"
        );
        int maxKeys = Math.max(1, Math.min(
                SmokeHarnessSupport.envInt("XAG_SMOKE_GEMINI_MAX_KEYS", null, keys.size()),
                keys.size()
        ));

        List<SmokeResult> results = new ArrayList<>();
        for (String key : keys.stream().limit(maxKeys).toList()) {
            results.add(runOneKey(baseUrl, key));
        }
        writeReport(baseUrl, results);

        assertTrue(
                results.stream().anyMatch(SmokeResult::success),
                "Gemini 真实 smoke 未成功；请查看 build/reports/xag-smoke/gemini-ai-studio.md 的脱敏分类。"
        );
    }

    private SmokeResult runOneKey(String baseUrl, String apiKey) {
        String keyRef = SmokeHarnessSupport.secretRef(apiKey);
        try {
            HttpResult models = get(baseUrl + "/v1beta/models", apiKey);
            String modelsStatus = classify(models.status(), models.body());
            if (models.status() != 200) {
                return new SmokeResult(keyRef, null, modelsStatus, "skipped_after_models_list", false, errorStatus(models.body()));
            }
            String model = selectModel(models.body());
            if (model == null || model.isBlank()) {
                return new SmokeResult(keyRef, null, "success", "provider_unsupported:no_generateContent_model", false, null);
            }
            HttpResult generation = postGenerateContent(baseUrl, model, apiKey);
            String generationStatus = classify(generation.status(), generation.body());
            boolean success = generation.status() == 200 && !responseText(generation.body()).isBlank();
            return new SmokeResult(
                    keyRef,
                    model,
                    modelsStatus,
                    generationStatus,
                    success,
                    success ? "responseTextChars=" + responseText(generation.body()).length() : errorStatus(generation.body())
            );
        } catch (RuntimeException exception) {
            return new SmokeResult(keyRef, null, "network_or_client_error", "skipped_after_exception", false,
                    exception.getClass().getSimpleName());
        }
    }

    private HttpResult get(String url, String apiKey) {
        return webClient.get()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new HttpResult(response.statusCode().value(), body)))
                .block(REQUEST_TIMEOUT);
    }

    private HttpResult postGenerateContent(String baseUrl, String model, String apiKey) {
        String body = """
                {
                  "contents": [
                    {
                      "role": "user",
                      "parts": [
                        {
                          "text": "Reply with exactly: xag-smoke-ok"
                        }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "temperature": 0,
                    "maxOutputTokens": 16
                  }
                }
                """;
        return webClient.post()
                .uri(baseUrl + "/v1beta/" + model + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(responseBody -> new HttpResult(response.statusCode().value(), responseBody)))
                .block(REQUEST_TIMEOUT);
    }

    private String selectModel(String body) {
        String configured = SmokeHarnessSupport.env("XAG_SMOKE_GEMINI_MODEL", "GEMINI_MODEL", "");
        if (!configured.isBlank()) {
            return configured.startsWith("models/") ? configured : "models/" + configured;
        }
        JsonNode models = read(body).path("models");
        for (String preferred : PREFERRED_MODELS) {
            for (JsonNode model : models) {
                if (preferred.equals(model.path("name").asText()) && supportsGenerateContent(model)) {
                    return preferred;
                }
            }
        }
        for (JsonNode model : models) {
            if (supportsGenerateContent(model)) {
                return model.path("name").asText();
            }
        }
        return null;
    }

    private boolean supportsGenerateContent(JsonNode model) {
        for (JsonNode method : model.path("supportedGenerationMethods")) {
            if ("generateContent".equals(method.asText())) {
                return true;
            }
        }
        return false;
    }

    private String classify(int status, String body) {
        if (status == 200) {
            return "success";
        }
        if (status == 400) {
            return "request_invalid:" + errorStatus(body);
        }
        if (status == 401 || status == 403) {
            return "auth_failed:" + errorStatus(body);
        }
        if (status == 404) {
            return "provider_unsupported:" + errorStatus(body);
        }
        if (status == 429) {
            return "rate_limited:" + errorStatus(body);
        }
        if (status >= 500) {
            return "upstream_error:" + errorStatus(body);
        }
        return "http_" + status + ":" + errorStatus(body);
    }

    private String errorStatus(String body) {
        String status = read(body).path("error").path("status").asText("");
        return status.isBlank() ? "none" : status;
    }

    private String responseText(String body) {
        return read(body)
                .path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText("");
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

    private void writeReport(String baseUrl, List<SmokeResult> results) {
        StringBuilder report = new StringBuilder()
                .append("- provider: gemini\n")
                .append("- baseUrl: ").append(baseUrl).append("\n")
                .append("- executedKeys: ").append(results.size()).append("\n")
                .append("- secretPolicy: report uses sha256 prefix references only\n\n")
                .append("| keyRef | model | modelsList | generateContent | success | detail |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (SmokeResult result : results) {
            report.append("| ")
                    .append(result.keyRef())
                    .append(" | ")
                    .append(result.model() == null ? "-" : result.model())
                    .append(" | ")
                    .append(result.modelsListStatus())
                    .append(" | ")
                    .append(result.generateContentStatus())
                    .append(" | ")
                    .append(result.success())
                    .append(" | ")
                    .append(result.detail() == null ? "-" : result.detail())
                    .append(" |\n");
        }
        SmokeHarnessSupport.writeReport("gemini-ai-studio", report.toString());
    }

    private record HttpResult(int status, String body) {
    }

    private record SmokeResult(
            String keyRef,
            String model,
            String modelsListStatus,
            String generateContentStatus,
            boolean success,
            String detail
    ) {
    }
}
