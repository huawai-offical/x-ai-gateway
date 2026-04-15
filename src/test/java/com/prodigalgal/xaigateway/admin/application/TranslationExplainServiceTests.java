package com.prodigalgal.xaigateway.admin.application;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.admin.api.TranslationExplainRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropCapabilityLevel;
import com.prodigalgal.xaigateway.gateway.core.interop.InteropFeature;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationExplainServiceTests {

    @Test
    void shouldReturnCompilerPlanDirectly() {
        TranslationExecutionPlanCompiler compiler = Mockito.mock(TranslationExecutionPlanCompiler.class);
        TranslationExplainService service = new TranslationExplainService(compiler);
        CanonicalExecutionPlan plan = new CanonicalExecutionPlan(
                true,
                CanonicalIngressProtocol.OPENAI,
                "/v1/chat/completions",
                "gpt-4o",
                "gpt-4o",
                "gpt-4o",
                TranslationResourceType.CHAT,
                TranslationOperation.CHAT_COMPLETION,
                ExecutionKind.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                InteropCapabilityLevel.NATIVE,
                List.of(InteropFeature.CHAT_TEXT),
                Map.of("chat_text", InteropCapabilityLevel.NATIVE),
                List.of(),
                List.of()
        );
        Mockito.when(compiler.compilePreview(
                        Mockito.eq("sk-gw-test"),
                        Mockito.eq("openai"),
                        Mockito.isNull(),
                        Mockito.eq("/v1/chat/completions"),
                        Mockito.eq("gpt-4o"),
                        Mockito.eq(GatewayDegradationPolicy.ALLOW_LOSSY),
                        Mockito.eq(GatewayClientFamily.GENERIC_OPENAI),
                        Mockito.any()
                ))
                .thenReturn(new CanonicalExecutionPlanCompilation(
                        plan,
                        null,
                        new GatewayRequestSemantics(TranslationResourceType.CHAT, TranslationOperation.CHAT_COMPLETION, List.of(InteropFeature.CHAT_TEXT), true),
                        new CanonicalRequest("sk-gw-test", CanonicalIngressProtocol.OPENAI, "/v1/chat/completions", "gpt-4o", List.of(), List.of(), null, null, null, null, null)
                ));

        CanonicalExecutionPlan result = service.explain(new TranslationExplainRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o",
                null,
                new ObjectMapper().createObjectNode().put("model", "gpt-4o")
        ));

        assertSame(plan, result);
        assertEquals("openai", result.ingressProtocol().name().toLowerCase());
    }
}
