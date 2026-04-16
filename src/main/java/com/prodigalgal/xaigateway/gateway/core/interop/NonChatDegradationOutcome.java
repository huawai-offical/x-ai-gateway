package com.prodigalgal.xaigateway.gateway.core.interop;

import java.util.List;

public record NonChatDegradationOutcome(
        SupportStatus supportStatus,
        InteropCapabilityLevel degradationLevel,
        List<String> blockedReasons,
        List<String> lossReasons,
        String renderPolicyReason,
        String fallbackPolicyReason
) {
    public NonChatDegradationOutcome {
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        lossReasons = lossReasons == null ? List.of() : List.copyOf(lossReasons);
        renderPolicyReason = renderPolicyReason == null ? "" : renderPolicyReason;
        fallbackPolicyReason = fallbackPolicyReason == null ? "" : fallbackPolicyReason;
    }
}
