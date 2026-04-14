package com.prodigalgal.xaigateway.admin.application;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.api.TranslationExplainRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionRequestMapping;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionResponseMapping;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertSame;

class TranslationExplainServiceTests {

    @Test
    void shouldReturnCompilerPlanDirectly() {
        TranslationExecutionPlanCompiler compiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        TranslationExplainService service = new TranslationExplainService(compiler);
        TranslationExecutionPlan plan = new TranslationExecutionPlan(
                true,
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
                GatewayClientFamily.GENERIC_OPENAI,
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                List.of(InteropFeature.CHAT_TEXT),
                Map.of("chat_text", InteropCapabilityLevel.NATIVE),
                null,
                null,
                null,
                ExecutionKind.NATIVE,
                InteropCapabilityLevel.NATIVE,
                "direct_upstream_execution",
                List.of(),
                List.of(),
                null,
                null,
                null,
                new TranslationExecutionRequestMapping("openai", "/v1/chat/completions", "gpt-4o", "gpt-4o", "gpt-4o", GatewayClientFamily.GENERIC_OPENAI, List.of(InteropFeature.CHAT_TEXT), Map.of("chat_text", InteropCapabilityLevel.NATIVE)),
                new TranslationExecutionResponseMapping(null, null, null, ExecutionKind.NATIVE, InteropCapabilityLevel.NATIVE, "direct_upstream_execution", null, null, null)
        );
        Mockito.when(compiler.compilePreview(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("openai"),
                        Mockito.eq("/v1/chat/completions"),
                        Mockito.eq("gpt-4o"),
                        Mockito.eq(GatewayDegradationPolicy.ALLOW_LOSSY),
                        Mockito.eq(GatewayClientFamily.GENERIC_OPENAI),
                        Mockito.any()
                ))
                .thenReturn(new TranslationExecutionPlanCompilation(
                        plan,
                        null,
                        new GatewayRequestSemantics(TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, List.of(InteropFeature.CHAT_TEXT), true)
                ));

        TranslationExecutionPlan result = service.explain(new TranslationExplainRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                null,
                new ObjectMapper().createObjectNode().put("model", "gpt-4o")
        ));

        assertSame(plan, result);
    }
}
