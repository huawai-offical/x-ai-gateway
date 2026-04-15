package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionSupportMatrixServiceTests {

    private final ExecutionSupportMatrixService service = new ExecutionSupportMatrixService();

    @Test
    void shouldResolveNativeStatusForNativeBackend() {
        assertEquals(
                SupportStatus.NATIVE,
                service.supportStatus(ExecutionBackend.NATIVE, InteropCapabilityLevel.NATIVE, List.of())
        );
    }

    @Test
    void shouldResolvePassthroughStatusForPassthroughBackend() {
        assertEquals(
                SupportStatus.PASSTHROUGH,
                service.supportStatus(ExecutionBackend.PASSTHROUGH, InteropCapabilityLevel.NATIVE, List.of())
        );
    }

    @Test
    void shouldResolveOrchestrationStatusForOrchestrationBackend() {
        assertEquals(
                SupportStatus.ORCHESTRATION,
                service.supportStatus(ExecutionBackend.ORCHESTRATION, InteropCapabilityLevel.NATIVE, List.of())
        );
    }

    @Test
    void shouldResolveDegradedStatusForLossyCapability() {
        assertEquals(
                SupportStatus.DEGRADED,
                service.supportStatus(ExecutionBackend.NATIVE, InteropCapabilityLevel.EMULATED, List.of())
        );
        assertEquals(
                InteropCapabilityLevel.EMULATED,
                service.degradationLevel(InteropCapabilityLevel.EMULATED, List.of())
        );
    }

    @Test
    void shouldResolveBlockedStatusWhenBlockersExist() {
        assertEquals(
                SupportStatus.BLOCKED,
                service.supportStatus(ExecutionBackend.ORCHESTRATION, InteropCapabilityLevel.NATIVE, List.of("missing feature"))
        );
        assertEquals(
                InteropCapabilityLevel.UNSUPPORTED,
                service.degradationLevel(InteropCapabilityLevel.NATIVE, List.of("missing feature"))
        );
    }
}
