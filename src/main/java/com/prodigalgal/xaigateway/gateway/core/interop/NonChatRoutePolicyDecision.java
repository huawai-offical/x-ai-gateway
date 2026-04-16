package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;

public record NonChatRoutePolicyDecision(
        RouteSelectionMode selectionMode,
        ExecutionBackend preferredBackend,
        List<ExecutionBackend> supportedBackends,
        InteropCapabilityLevel executionCapabilityLevel,
        InteropCapabilityLevel renderCapabilityLevel,
        InteropCapabilityLevel overallCapabilityLevel,
        SupportStatus supportStatus,
        String objectMode,
        List<String> blockedReasons,
        List<String> lossReasons,
        String policyReason
) {
    public NonChatRoutePolicyDecision {
        supportedBackends = supportedBackends == null ? List.of() : List.copyOf(supportedBackends);
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        lossReasons = lossReasons == null ? List.of() : List.copyOf(lossReasons);
        policyReason = policyReason == null ? "" : policyReason;
    }

    public ExecutionKind executionKind() {
        if (!blockedReasons.isEmpty() || overallCapabilityLevel == InteropCapabilityLevel.UNSUPPORTED) {
            return ExecutionKind.BLOCKED;
        }
        return switch (overallCapabilityLevel) {
            case NATIVE -> ExecutionKind.NATIVE;
            case EMULATED -> ExecutionKind.EMULATED;
            case LOSSY -> ExecutionKind.TRANSLATED;
            case UNSUPPORTED -> ExecutionKind.BLOCKED;
        };
    }
}
