package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OpenAiDirectSmokeCertificationResponse(
        Long credentialId,
        String certificationStatus,
        boolean dryRun,
        Instant generatedAt,
        Map<String, Integer> summary,
        List<OpenAiDirectSmokeCertificationFixture> fixtureSnapshots,
        OpenAiDirectSmokeRecordReplayFixture recordReplayFixture,
        OpenAiDirectResourceSmokeResponse smoke
) {
}
