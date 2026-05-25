package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record InvitationLeaderboardEntryResponse(
        Long userId,
        String email,
        String displayName,
        long directInviteCount,
        long totalInviteCount,
        long referrerRewardTokenCredits,
        Instant latestInviteAt
) {
}
