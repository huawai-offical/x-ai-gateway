package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeCertificationFixture;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeCertificationResponse;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeItemResponse;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeRecordReplayFixture;
import com.prodigalgal.xaigateway.admin.api.FunctionalProviderSmokeResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class FunctionalProviderSmokeCertificationService {

    static final String RECORD_REPLAY_SCHEMA_VERSION = "2026-05-19.functional-provider-smoke-record-replay.v1";

    FunctionalProviderSmokeCertificationResponse certify(FunctionalProviderSmokeResponse smoke, Instant generatedAt) {
        List<FunctionalProviderSmokeCertificationFixture> fixtures = smoke.items().stream()
                .map(this::fixture)
                .toList();
        String certificationStatus = certificationStatus(smoke.summary(), smoke.dryRun());
        FunctionalProviderSmokeRecordReplayFixture recordReplayFixture = recordReplayFixture(
                smoke,
                generatedAt,
                certificationStatus,
                fixtures
        );
        return new FunctionalProviderSmokeCertificationResponse(
                smoke.credentialId(),
                certificationStatus,
                smoke.dryRun(),
                generatedAt,
                Map.copyOf(smoke.summary()),
                fixtures,
                recordReplayFixture,
                smoke
        );
    }

    Map<String, Object> metadata(FunctionalProviderSmokeCertificationResponse certification) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("certificationStatus", certification.certificationStatus());
        result.put("dryRun", certification.dryRun());
        result.put("generatedAt", certification.generatedAt().toString());
        result.put("summary", certification.summary());
        List<Map<String, Object>> fixtures = new ArrayList<>();
        for (FunctionalProviderSmokeCertificationFixture fixture : certification.fixtureSnapshots()) {
            fixtures.add(fixtureMetadata(fixture));
        }
        result.put("fixtureSnapshots", fixtures);
        result.put("recordReplayFixture", recordReplayMetadata(certification.recordReplayFixture()));
        return sanitizeMap(result);
    }

    private FunctionalProviderSmokeRecordReplayFixture recordReplayFixture(
            FunctionalProviderSmokeResponse smoke,
            Instant generatedAt,
            String certificationStatus,
            List<FunctionalProviderSmokeCertificationFixture> fixtures) {
        Map<String, Object> replayPolicy = new LinkedHashMap<>();
        replayPolicy.put("network", "disabled_by_default");
        replayPolicy.put("billableOperations", "replay_only");
        replayPolicy.put("writeOperations", "replay_only");
        replayPolicy.put("secretMaterial", "redacted");
        replayPolicy.put("dryRunEvidenceAccepted", smoke.dryRun());
        replayPolicy.put("fixtureSource", "functional_provider_smoke_certification");
        replayPolicy.put("liveExecutionRequiresAllowLive", true);
        replayPolicy.put("billableExecutionRequiresAllowBillableProbes", true);
        return new FunctionalProviderSmokeRecordReplayFixture(
                RECORD_REPLAY_SCHEMA_VERSION,
                "record_replay",
                smoke.providerType(),
                smoke.protocol(),
                sanitizeText(smoke.baseUrl()),
                baseUrlHost(smoke.baseUrl()),
                certificationStatus,
                smoke.dryRun(),
                generatedAt,
                Map.copyOf(smoke.summary()),
                Map.copyOf(replayPolicy),
                List.copyOf(fixtures)
        );
    }

    private Map<String, Object> recordReplayMetadata(FunctionalProviderSmokeRecordReplayFixture fixture) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", fixture.schemaVersion());
        result.put("replayMode", fixture.replayMode());
        result.put("providerType", fixture.providerType().name());
        result.put("protocol", fixture.protocol());
        result.put("baseUrl", fixture.baseUrl());
        result.put("baseUrlHost", fixture.baseUrlHost());
        result.put("certificationStatus", fixture.certificationStatus());
        result.put("dryRun", fixture.dryRun());
        result.put("recordedAt", fixture.recordedAt().toString());
        result.put("summary", fixture.summary());
        result.put("replayPolicy", fixture.replayPolicy());
        List<Map<String, Object>> fixtures = new ArrayList<>();
        for (FunctionalProviderSmokeCertificationFixture child : fixture.fixtures()) {
            fixtures.add(fixtureMetadata(child));
        }
        result.put("fixtures", fixtures);
        return result;
    }

    private Map<String, Object> fixtureMetadata(FunctionalProviderSmokeCertificationFixture fixture) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("providerType", fixture.providerType().name());
        item.put("protocol", fixture.protocol());
        item.put("resourceFamily", fixture.resourceFamily());
        item.put("status", fixture.status());
        item.put("classification", fixture.classification());
        putIfPresent(item, "skippedReason", fixture.skippedReason());
        item.put("method", fixture.method());
        item.put("path", fixture.path());
        item.put("model", fixture.model());
        item.put("billable", fixture.billable());
        item.put("writeOperation", fixture.writeOperation());
        putIfPresent(item, "httpStatus", fixture.httpStatus());
        putIfPresent(item, "upstreamRequestId", fixture.upstreamRequestId());
        putIfPresent(item, "durationMs", fixture.durationMs());
        putIfPresent(item, "failureType", fixture.failureType());
        putIfPresent(item, "failureMessage", fixture.failureMessage());
        item.put("evidence", fixture.evidence());
        item.put("requestPreview", fixture.requestPreview());
        return item;
    }

    private FunctionalProviderSmokeCertificationFixture fixture(FunctionalProviderSmokeItemResponse item) {
        return new FunctionalProviderSmokeCertificationFixture(
                item.providerType(),
                item.protocol(),
                item.resourceFamily(),
                item.status(),
                item.classification(),
                item.skippedReason(),
                item.method(),
                item.path(),
                item.model(),
                item.billable(),
                item.writeOperation(),
                item.httpStatus(),
                item.upstreamRequestId(),
                item.durationMs(),
                item.failureType(),
                sanitizeText(item.failureMessage()),
                sanitizeMap(item.evidence()),
                sanitizeMap(item.requestPreview())
        );
    }

    private String certificationStatus(Map<String, Integer> summary, boolean dryRun) {
        if (dryRun) {
            return "DRY_RUN";
        }
        if (count(summary, "FAIL") > 0) {
            return "FAILED";
        }
        if (count(summary, "NO_PERMISSION") > 0) {
            return "NO_PERMISSION";
        }
        if (count(summary, "PASS") > 0 && count(summary, "BUDGET_BLOCKED") > 0) {
            return "PARTIAL_CERTIFIED";
        }
        if (count(summary, "PASS") > 0) {
            return "CERTIFIED";
        }
        if (count(summary, "BUDGET_BLOCKED") > 0) {
            return "BUDGET_BLOCKED";
        }
        if (count(summary, "UNSUPPORTED") > 0) {
            return "UNSUPPORTED";
        }
        return "SKIPPED";
    }

    private int count(Map<String, Integer> summary, String key) {
        return summary.getOrDefault(key, 0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Object sanitized = sanitize(source);
        return sanitized instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }

    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                if (key != null) {
                    String normalizedKey = String.valueOf(key);
                    sanitized.put(normalizedKey, isSensitiveKey(normalizedKey) ? "***" : sanitize(child));
                }
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>();
            for (Object child : list) {
                sanitized.add(sanitize(child));
            }
            return sanitized;
        }
        if (value instanceof String text) {
            return sanitizeText(text);
        }
        return value;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***");
        sanitized = sanitized.replaceAll("sk-[A-Za-z0-9._~+/=-]+", "sk-***");
        sanitized = sanitized.replaceAll("AIza[A-Za-z0-9_-]{10,}", "AIza***");
        sanitized = sanitized.replaceAll("(?i)(api-key|x-api-key|x-goog-api-key)\\s*[:=]\\s*[^\\s,;}]+", "$1=***");
        sanitized = sanitized.replaceAll("\\borg[-_][A-Za-z0-9._~+-]+", "org-***");
        sanitized = sanitized.replaceAll("\\bproj[-_][A-Za-z0-9._~+-]+", "proj-***");
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("api-key")
                || normalized.contains("x-api-key")
                || normalized.contains("x-goog-api-key")
                || normalized.contains("organization")
                || normalized.contains("project")
                || normalized.contains("cookie")
                || "key".equals(normalized);
    }

    private String baseUrlHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(baseUrl);
            return sanitizeText(uri.getHost());
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
