package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalInvitationUserResponse(
        Long userId,
        String email,
        String displayName,
        Instant invitedAt
) {
}
