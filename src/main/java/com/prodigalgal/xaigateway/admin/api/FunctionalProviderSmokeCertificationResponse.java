package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FunctionalProviderSmokeCertificationResponse(
        Long credentialId,
        String certificationStatus,
        boolean dryRun,
        Instant generatedAt,
        Map<String, Integer> summary,
        List<FunctionalProviderSmokeCertificationFixture> fixtureSnapshots,
        FunctionalProviderSmokeRecordReplayFixture recordReplayFixture,
        FunctionalProviderSmokeResponse smoke
) {
}
