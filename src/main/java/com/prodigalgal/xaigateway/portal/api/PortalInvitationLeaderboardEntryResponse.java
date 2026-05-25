package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalInvitationLeaderboardEntryResponse(
        Long userId,
        String displayName,
        long directInviteCount,
        long totalInviteCount,
        long referrerRewardTokenCredits,
        Instant latestInviteAt
) {
}
