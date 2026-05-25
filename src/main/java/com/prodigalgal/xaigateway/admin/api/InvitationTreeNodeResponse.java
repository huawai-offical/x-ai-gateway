package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record InvitationTreeNodeResponse(
        Long userId,
        String email,
        String displayName,
        int depth,
        Instant invitedAt,
        List<InvitationTreeNodeResponse> children
) {
}
