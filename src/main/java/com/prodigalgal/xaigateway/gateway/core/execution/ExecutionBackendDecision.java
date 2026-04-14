package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;

public record ExecutionBackendDecision(
        ExecutionBackend preferredBackend,
        List<ExecutionBackend> supportedBackends,
        String reason
) {
    public ExecutionBackendDecision {
        supportedBackends = supportedBackends == null ? List.of() : List.copyOf(supportedBackends);
    }
}
