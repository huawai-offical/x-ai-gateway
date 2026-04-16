package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;

public interface NonChatCanonicalRenderer {

    boolean supports(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics
    );

    default InteropCapabilityLevel renderCapabilityLevel(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        return supports(ingressProtocol, requestPath, semantics)
                ? InteropCapabilityLevel.NATIVE
                : InteropCapabilityLevel.UNSUPPORTED;
    }

    NonChatRenderedResponse render(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            GatewayResourceExecutionResult result
    );

    default boolean supportsNativeView(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics,
            Object nativeView
    ) {
        return false;
    }

    default NonChatRenderedResponse renderNativeView(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            Object nativeView
    ) {
        throw new IllegalArgumentException("当前 renderer 不支持 native view render。");
    }
}
