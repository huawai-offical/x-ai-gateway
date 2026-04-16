package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionResult;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessageBatchesEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class AnthropicNativeNonChatCanonicalRenderer implements NonChatCanonicalRenderer {

    private final AnthropicMessageBatchesEncoder anthropicMessageBatchesEncoder;

    public AnthropicNativeNonChatCanonicalRenderer(AnthropicMessageBatchesEncoder anthropicMessageBatchesEncoder) {
        this.anthropicMessageBatchesEncoder = anthropicMessageBatchesEncoder;
    }

    @Override
    public boolean supports(
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            GatewayRequestSemantics semantics
    ) {
        return ingressProtocol == CanonicalIngressProtocol.ANTHROPIC_NATIVE
                && semantics != null
                && switch (semantics.operation()) {
                    case ANTHROPIC_MESSAGE_BATCH_CREATE,
                            ANTHROPIC_MESSAGE_BATCH_GET,
                            ANTHROPIC_MESSAGE_BATCH_CANCEL -> true;
                    default -> false;
                };
    }

    @Override
    public NonChatRenderedResponse render(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan executionPlan,
            GatewayResourceExecutionResult result
    ) {
        JsonNode response = result.responseJson();
        if (response == null) {
            throw new IllegalStateException("Anthropic native render 缺少 JSON body。");
        }
        return new NonChatRenderedResponse(
                ResponseEntity.status(result.statusCode()).body(anthropicMessageBatchesEncoder.encode(response))
        );
    }
}
