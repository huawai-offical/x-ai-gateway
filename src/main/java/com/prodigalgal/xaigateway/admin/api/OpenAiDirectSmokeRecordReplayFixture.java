package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OpenAiDirectSmokeRecordReplayFixture(
        String schemaVersion,
        String replayMode,
        String providerType,
        String baseUrl,
        String certificationStatus,
        boolean dryRun,
        Instant recordedAt,
        Map<String, Integer> summary,
        Map<String, Object> replayPolicy,
        List<OpenAiDirectSmokeCertificationFixture> fixtures
) {
}
