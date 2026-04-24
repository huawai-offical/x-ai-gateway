package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalAnnouncementResponse(
        String id,
        String title,
        String summary,
        String body,
        boolean read,
        Instant publishedAt
) {
}
