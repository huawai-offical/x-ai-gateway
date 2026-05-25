package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalInvitationSummaryResponse(
        Long userId,
        long directInviteCount,
        long totalInviteCount,
        long referrerRewardTokenCredits,
        Instant latestInviteAt,
        List<PortalInvitationUserResponse> directInvites,
        List<PortalInvitationLeaderboardEntryResponse> leaderboard
) {
}
