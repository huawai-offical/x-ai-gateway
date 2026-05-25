package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeItemResponse;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeResponse;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("live-smoke")
class FunctionalProviderSmokeLiveGateTests {

    private static final String LIVE_GATE = "XAI_GATEWAY_FUNCTIONAL_PROVIDER_LIVE_SMOKE";
    private static final String BILLABLE_GATE = "XAI_GATEWAY_ALLOW_BILLABLE_SMOKE";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FunctionalProviderSmokeHttpClient client = new FunctionalProviderSmokeHttpClient(objectMapper);
    private final FunctionalProviderSmokeCertificationService certificationService =
            new FunctionalProviderSmokeCertificationService();
    private final FunctionalProviderSmokeRecordReplayFixtureVerifier verifier =
            new FunctionalProviderSmokeRecordReplayFixtureVerifier();

    @Test
    void shouldCertifyCohereNativeLiveSmokeWhenExplicitlyEnabled() {
        NativeProviderLiveConfig config = nativeProviderLiveConfig(
                "COHERE",
                "COHERE_NATIVE",
                "https://api.cohere.ai",
                "embed-v4.0",
                "rerank-v3.5",
                "COHERE_API_KEY",
                "XAI_GATEWAY_COHERE_API_KEY"
        );

        FunctionalProviderSmokeResponse smoke = executeLiveSmoke(config);
        assertCohereEvidence(smoke.items());
        assertCertifiedRecordReplayFixture(smoke, "COHERE", "COHERE_NATIVE");
    }

    @Test
    void shouldCertifyJinaNativeLiveSmokeWhenExplicitlyEnabled() {
        NativeProviderLiveConfig config = nativeProviderLiveConfig(
                "JINA",
                "JINA_NATIVE",
                "https://api.jina.ai",
                "jina-embeddings-v3",
                "jina-reranker-v2-base-multilingual",
                "JINA_API_KEY",
                "XAI_GATEWAY_JINA_API_KEY"
        );

        FunctionalProviderSmokeResponse smoke = executeLiveSmoke(config);
        assertJinaEvidence(smoke.items());
        assertCertifiedRecordReplayFixture(smoke, "JINA", "JINA_NATIVE");
    }

    private NativeProviderLiveConfig nativeProviderLiveConfig(
            String provider,
            String protocol,
            String defaultBaseUrl,
            String defaultEmbeddingModel,
            String defaultRerankModel,
            String... secretEnvNames) {
        assumeTrue(flag(LIVE_GATE), "未设置 " + LIVE_GATE + "=true，跳过真实 functional provider live smoke。");
        assumeTrue(flag(BILLABLE_GATE), "未设置 " + BILLABLE_GATE + "=true，跳过可能计费的真实 live smoke。");
        String secret = firstEnv(secretEnvNames);
        assumeTrue(secret != null && !secret.isBlank(),
                "未设置 " + String.join(" 或 ", secretEnvNames) + "，跳过 " + provider + " 真实 live smoke。");
        return new NativeProviderLiveConfig(
                provider,
                protocol,
                firstNonBlank(
                        firstEnv(provider + "_BASE_URL", "XAI_GATEWAY_" + provider + "_BASE_URL"),
                        defaultBaseUrl
                ),
                firstNonBlank(
                        firstEnv(provider + "_EMBED_MODEL", "XAI_GATEWAY_" + provider + "_EMBED_MODEL"),
                        defaultEmbeddingModel
                ),
                firstNonBlank(
                        firstEnv(provider + "_RERANK_MODEL", "XAI_GATEWAY_" + provider + "_RERANK_MODEL"),
                        defaultRerankModel
                ),
                secret
        );
    }

    private FunctionalProviderSmokeResponse executeLiveSmoke(NativeProviderLiveConfig config) {
        FunctionalProviderSmokeItemResponse embeddings = client.executeProbe(
                ProviderType.OPENAI_COMPATIBLE,
                config.protocol(),
                "EMBEDDINGS",
                config.secret(),
                config.baseUrl(),
                config.embeddingModel(),
                10,
                true
        );
        FunctionalProviderSmokeItemResponse rerank = client.executeProbe(
                ProviderType.OPENAI_COMPATIBLE,
                config.protocol(),
                "RERANK",
                config.secret(),
                config.baseUrl(),
                config.rerankModel(),
                10,
                true
        );
        List<FunctionalProviderSmokeItemResponse> items = List.of(embeddings, rerank);
        for (FunctionalProviderSmokeItemResponse item : items) {
            assertEquals("PASS", item.classification(), liveFailureMessage(config.provider(), item));
            assertEquals("LIVE_SMOKE_OK", item.status());
            assertEquals(200, item.httpStatus());
            assertTrue(item.requestPreview().toString().contains("Bearer ***"));
            assertTrue(item.upstreamRequestId() == null || !item.upstreamRequestId().isBlank());
        }
        return new FunctionalProviderSmokeResponse(
                0L,
                "LIVE_SMOKE_COMPLETED",
                "PASS",
                null,
                config.baseUrl(),
                ProviderType.OPENAI_COMPATIBLE,
                config.protocol(),
                false,
                true,
                true,
                null,
                "env-live-smoke",
                Instant.now(),
                config.provider() + " native live smoke completed.",
                summary(items),
                items
        );
    }

    private void assertCertifiedRecordReplayFixture(
            FunctionalProviderSmokeResponse smoke,
            String expectedProvider,
            String expectedProtocol) {
        var certification = certificationService.certify(smoke, Instant.now());
        var fixture = certification.recordReplayFixture();
        var verification = verifier.validate(
                objectMapper.valueToTree(certificationService.metadata(certification).get("recordReplayFixture")));

        assertEquals("CERTIFIED", certification.certificationStatus());
        assertEquals(expectedProvider, fixture.providerType());
        assertEquals(expectedProtocol, fixture.protocol());
        assertEquals(false, fixture.dryRun());
        assertEquals(2, fixture.summary().get("PASS"));
        assertEquals(true, verification.valid(), () -> String.join("\n", verification.errors()));
    }

    private void assertCohereEvidence(List<FunctionalProviderSmokeItemResponse> items) {
        Map<String, Object> embeddings = evidence(items, "EMBEDDINGS");
        assertTrue(positiveNumber(embeddings.get("embeddingFloatVectorsSeen")));
        assertTrue(String.valueOf(embeddings.get("embeddingFields")).contains("float"));
        assertTrue(String.valueOf(embeddings.get("billedUnitFields")).contains("input_tokens"));

        Map<String, Object> rerank = evidence(items, "RERANK");
        assertTrue(positiveNumber(rerank.get("resultsSeen")));
        assertTrue(String.valueOf(rerank.get("firstResultFields")).contains("relevance_score"));
        assertTrue(String.valueOf(rerank.get("billedUnitFields")).contains("search_units"));
    }

    private void assertJinaEvidence(List<FunctionalProviderSmokeItemResponse> items) {
        Map<String, Object> embeddings = evidence(items, "EMBEDDINGS");
        assertTrue(positiveNumber(embeddings.get("dataSeen")) || positiveNumber(embeddings.get("embeddingsSeen")));

        Map<String, Object> rerank = evidence(items, "RERANK");
        assertTrue(positiveNumber(rerank.get("resultsSeen")));
        assertTrue(String.valueOf(rerank.get("firstResultFields")).contains("relevance_score"));
    }

    private Map<String, Object> evidence(List<FunctionalProviderSmokeItemResponse> items, String resourceFamily) {
        return items.stream()
                .filter(item -> resourceFamily.equals(item.resourceFamily()))
                .findFirst()
                .map(FunctionalProviderSmokeItemResponse::evidence)
                .orElse(Map.of());
    }

    private Map<String, Integer> summary(List<FunctionalProviderSmokeItemResponse> items) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (String classification : List.of("PASS", "FAIL", "SKIPPED", "UNSUPPORTED", "NO_PERMISSION", "BUDGET_BLOCKED")) {
            summary.put(classification, 0);
        }
        for (FunctionalProviderSmokeItemResponse item : items) {
            summary.computeIfPresent(item.classification(), (key, value) -> value + 1);
        }
        return summary;
    }

    private String liveFailureMessage(String provider, FunctionalProviderSmokeItemResponse item) {
        return provider + " " + item.resourceFamily()
                + " live smoke 未通过，classification=" + item.classification()
                + ", status=" + item.status()
                + ", skippedReason=" + item.skippedReason()
                + ", failureType=" + item.failureType()
                + ", failureMessage=" + item.failureMessage()
                + ", evidence=" + item.evidence();
    }

    private boolean positiveNumber(Object value) {
        return value instanceof Number number && number.longValue() > 0;
    }

    private boolean flag(String name) {
        return Boolean.parseBoolean(System.getenv(name));
    }

    private String firstEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record NativeProviderLiveConfig(
            String provider,
            String protocol,
            String baseUrl,
            String embeddingModel,
            String rerankModel,
            String secret) {
    }
}
