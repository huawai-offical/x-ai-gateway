package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FunctionalProviderSmokeRecordReplayFixture(
        String schemaVersion,
        String replayMode,
        ProviderType providerType,
        String protocol,
        String baseUrl,
        String baseUrlHost,
        String certificationStatus,
        boolean dryRun,
        Instant recordedAt,
        Map<String, Integer> summary,
        Map<String, Object> replayPolicy,
        List<FunctionalProviderSmokeCertificationFixture> fixtures
) {
}
