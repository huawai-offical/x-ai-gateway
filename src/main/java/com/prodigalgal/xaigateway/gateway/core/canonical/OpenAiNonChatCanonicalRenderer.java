package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import org.springframework.stereotype.Component;

@Component
public class OpenAiNonChatCanonicalRenderer implements NonChatCanonicalRenderer {

    @Override
    public boolean supports(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        return ingressProtocol == CanonicalIngressProtocol.OPENAI
                && semantics != null
                && semantics.resourceType() != TranslationResourceType.CHAT
                && semantics.resourceType() != TranslationResourceType.RESPONSE
                && requestPath != null
                && requestPath.startsWith("/v1/");
    }

    @Override
    public InteropCapabilityLevel renderCapabilityLevel(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        return supports(ingressProtocol, requestPath, semantics)
                ? InteropCapabilityLevel.NATIVE
                : InteropCapabilityLevel.UNSUPPORTED;
    }

    @Override
    public NonChatRenderedResponse render(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            GatewayResourceExecutionResult result
    ) {
        if (result.binary()) {
            return new NonChatRenderedResponse(result.binaryResponse());
        }
        return new NonChatRenderedResponse(result.jsonResponse());
    }
}
