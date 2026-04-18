package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record IncidentSummaryResponse(
        OpsSummaryResponse opsSummary,
        OpsSloSummaryResponse sloSummary,
        OpsCapacitySummaryResponse capacitySummary,
        GovernanceHealthScoreResponse healthScores,
        List<OpsAlertEventResponse> incidents,
        List<AlertSilenceResponse> silences,
        List<QuarantineRecordResponse> quarantines,
        List<IncidentEntityResponse> affectedEntities,
        List<IncidentTimelineEventResponse> timeline,
        List<String> recommendedActions
) {
}
