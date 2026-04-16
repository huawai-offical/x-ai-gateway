package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import java.util.List;

public record NonChatTargetResolution(
        RouteSelectionMode selectionMode,
        CatalogCandidateView candidate,
        String policyReason,
        List<String> blockedReasons
) {
    public NonChatTargetResolution {
        policyReason = policyReason == null ? "" : policyReason;
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
    }

    public boolean resolved() {
        return candidate != null;
    }
}
