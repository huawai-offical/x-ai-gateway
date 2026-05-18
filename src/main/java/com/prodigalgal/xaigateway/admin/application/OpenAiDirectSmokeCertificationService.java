package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeItemResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectResourceSmokeResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeCertificationFixture;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeCertificationResponse;
import com.prodigalgal.xaigateway.admin.api.OpenAiDirectSmokeRecordReplayFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class OpenAiDirectSmokeCertificationService {

    static final String RECORD_REPLAY_SCHEMA_VERSION = "2026-05-16.openai-direct-smoke-record-replay.v1";

    OpenAiDirectSmokeCertificationResponse certify(OpenAiDirectResourceSmokeResponse smoke, Instant generatedAt) {
        List<OpenAiDirectSmokeCertificationFixture> fixtures = smoke.items().stream()
                .map(this::fixture)
                .toList();
        String certificationStatus = certificationStatus(smoke.summary(), smoke.dryRun());
        OpenAiDirectSmokeRecordReplayFixture recordReplayFixture = recordReplayFixture(
                smoke,
                generatedAt,
                certificationStatus,
                fixtures
        );
        return new OpenAiDirectSmokeCertificationResponse(
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

    Map<String, Object> metadata(OpenAiDirectSmokeCertificationResponse certification) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("certificationStatus", certification.certificationStatus());
        result.put("dryRun", certification.dryRun());
        result.put("generatedAt", certification.generatedAt().toString());
        result.put("summary", certification.summary());
        List<Map<String, Object>> fixtures = new ArrayList<>();
        for (OpenAiDirectSmokeCertificationFixture fixture : certification.fixtureSnapshots()) {
            fixtures.add(fixtureMetadata(fixture));
        }
        result.put("fixtureSnapshots", fixtures);
        result.put("recordReplayFixture", recordReplayMetadata(certification.recordReplayFixture()));
        return sanitizeMap(result);
    }

    private OpenAiDirectSmokeRecordReplayFixture recordReplayFixture(
            OpenAiDirectResourceSmokeResponse smoke,
            Instant generatedAt,
            String certificationStatus,
            List<OpenAiDirectSmokeCertificationFixture> fixtures) {
        Map<String, Object> replayPolicy = new LinkedHashMap<>();
        replayPolicy.put("network", "disabled_by_default");
        replayPolicy.put("billableOperations", "replay_only");
        replayPolicy.put("writeOperations", "replay_only");
        replayPolicy.put("secretMaterial", "redacted");
        replayPolicy.put("dryRunEvidenceAccepted", smoke.dryRun());
        replayPolicy.put("fixtureSource", "openai_direct_resource_smoke_certification");
        return new OpenAiDirectSmokeRecordReplayFixture(
                RECORD_REPLAY_SCHEMA_VERSION,
                "record_replay",
                smoke.providerType().name(),
                sanitizeText(smoke.baseUrl()),
                certificationStatus,
                smoke.dryRun(),
                generatedAt,
                Map.copyOf(smoke.summary()),
                Map.copyOf(replayPolicy),
                List.copyOf(fixtures)
        );
    }

    private Map<String, Object> recordReplayMetadata(OpenAiDirectSmokeRecordReplayFixture fixture) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", fixture.schemaVersion());
        result.put("replayMode", fixture.replayMode());
        result.put("providerType", fixture.providerType());
        result.put("baseUrl", fixture.baseUrl());
        result.put("certificationStatus", fixture.certificationStatus());
        result.put("dryRun", fixture.dryRun());
        result.put("recordedAt", fixture.recordedAt().toString());
        result.put("summary", fixture.summary());
        result.put("replayPolicy", fixture.replayPolicy());
        List<Map<String, Object>> fixtures = new ArrayList<>();
        for (OpenAiDirectSmokeCertificationFixture child : fixture.fixtures()) {
            fixtures.add(fixtureMetadata(child));
        }
        result.put("fixtures", fixtures);
        return result;
    }

    private Map<String, Object> fixtureMetadata(OpenAiDirectSmokeCertificationFixture fixture) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("resourceFamily", fixture.resourceFamily());
        item.put("status", fixture.status());
        item.put("classification", fixture.classification());
        putIfPresent(item, "skippedReason", fixture.skippedReason());
        item.put("method", fixture.method());
        item.put("path", fixture.path());
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

    private OpenAiDirectSmokeCertificationFixture fixture(OpenAiDirectResourceSmokeItemResponse item) {
        return new OpenAiDirectSmokeCertificationFixture(
                item.resourceFamily(),
                item.status(),
                item.classification(),
                item.skippedReason(),
                item.method(),
                item.path(),
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
        String sanitized = value.replaceAll("Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***");
        sanitized = sanitized.replaceAll("sk-[A-Za-z0-9._~+/=-]+", "sk-***");
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
                || normalized.contains("organization")
                || normalized.contains("project")
                || normalized.contains("cookie");
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
