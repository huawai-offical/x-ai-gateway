package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record RunbookLinkRequest(
        @NotBlank String linkName,
        String eventType,
        String entityType,
        @NotBlank String linkUrl,
        String description,
        Boolean enabled
) {
}
