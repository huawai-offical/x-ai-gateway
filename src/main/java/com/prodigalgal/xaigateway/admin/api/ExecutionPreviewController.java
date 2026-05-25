package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlanCompilation;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayDegradationPolicy;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompiler;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.PreparedChatExecution;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/execution")
public class ExecutionPreviewController {

    private final GatewayRouteSelectionService gatewayRouteSelectionService;
    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final TranslationExecutionPlanCompiler translationExecutionPlanCompiler;

    public ExecutionPreviewController(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ProviderExecutionSupportService providerExecutionSupportService,
            TranslationExecutionPlanCompiler translationExecutionPlanCompiler) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.translationExecutionPlanCompiler = translationExecutionPlanCompiler;
    }

    @PostMapping("/preview")
    public ExecutionPreviewResponse preview(@RequestBody RouteSelectionPreviewRequest request) {
        RouteSelectionResult selectionResult = gatewayRouteSelectionService.select(new RouteSelectionRequest(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.requestPath(),
                request.requestedModel(),
                request.requestBody(),
                GatewayClientFamily.GENERIC_OPENAI,
                false,
                null,
                request.httpMethod()
        ));
        CanonicalExecutionPlanCompilation compilation = translationExecutionPlanCompiler.compilePreview(
                request.distributedKeyPrefix(),
                request.protocol(),
                request.httpMethod(),
                request.requestPath(),
                request.requestedModel(),
                GatewayDegradationPolicy.STRICT,
                GatewayClientFamily.GENERIC_OPENAI,
                request.requestBody()
        );

        ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
        return switch (providerType) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> previewOpenAi(selectionResult, compilation);
            case ANTHROPIC_DIRECT -> previewAnthropic(selectionResult, compilation);
            case GEMINI_DIRECT -> previewGemini(selectionResult, compilation);
            case OLLAMA_DIRECT -> buildResponse(selectionResult, compilation, Map.of());
        };
    }

    private ExecutionPreviewResponse previewOpenAi(RouteSelectionResult selectionResult, CanonicalExecutionPlanCompilation compilation) {
        PreparedChatExecution<OpenAiChatOptions> prepared = providerExecutionSupportService.prepareOpenAi(
                selectionResult,
                OpenAiChatOptions.builder().model(selectionResult.resolvedModelKey()).build(),
                java.util.List.of(),
                null
        );
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("model", prepared.options().getModel());
        options.put("promptCacheKey", prepared.options().getPromptCacheKey());
        options.put("metadata", prepared.options().getMetadata());
        return buildResponse(selectionResult, compilation, options);
    }

    private ExecutionPreviewResponse previewAnthropic(RouteSelectionResult selectionResult, CanonicalExecutionPlanCompilation compilation) {
        PreparedChatExecution<AnthropicChatOptions> prepared = providerExecutionSupportService.prepareAnthropic(
                selectionResult,
                AnthropicChatOptions.builder().model(selectionResult.resolvedModelKey()).build()
        );
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("model", prepared.options().getModel());
        options.put("cacheOptions", prepared.options().getCacheOptions());
        options.put("metadata", prepared.options().getMetadata());
        return buildResponse(selectionResult, compilation, options);
    }

    private ExecutionPreviewResponse previewGemini(RouteSelectionResult selectionResult, CanonicalExecutionPlanCompilation compilation) {
        PreparedChatExecution<GoogleGenAiChatOptions> prepared = providerExecutionSupportService.prepareGemini(
                selectionResult,
                GoogleGenAiChatOptions.builder().model(selectionResult.resolvedModelKey()).build()
        );
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("model", prepared.options().getModel());
        options.put("useCachedContent", prepared.options().getUseCachedContent());
        options.put("cachedContentName", prepared.options().getCachedContentName());
        options.put("autoCacheThreshold", prepared.options().getAutoCacheThreshold());
        options.put("autoCacheTtl", prepared.options().getAutoCacheTtl());
        options.put("labels", prepared.options().getLabels());
        return buildResponse(selectionResult, compilation, options);
    }

    private ExecutionPreviewResponse buildResponse(
            RouteSelectionResult selectionResult,
            CanonicalExecutionPlanCompilation compilation,
            Map<String, Object> providerOptions) {
        return new ExecutionPreviewResponse(
                selectionResult,
                compilation.canonicalRequest(),
                compilation.canonicalPlan(),
                selectionResult.selectedCandidate(),
                providerOptions,
                buildTranslatedPayload(selectionResult, compilation.canonicalRequest(), compilation.canonicalPlan(), providerOptions),
                buildBindingSummary(selectionResult.selectedCandidate()),
                buildNormalizedPreview(compilation.canonicalPlan())
        );
    }

    private ExecutionPreviewPayloadResponse buildTranslatedPayload(
            RouteSelectionResult selectionResult,
            CanonicalRequest canonicalRequest,
            com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan plan,
            Map<String, Object> providerOptions) {
        return new ExecutionPreviewPayloadResponse(
                selectionResult.selectedCandidate().candidate().providerType().name(),
                selectionResult.resolvedModelKey(),
                canonicalRequest.requestPath(),
                plan.objectMode(),
                canonicalRequest.messages().stream()
                        .map(this::toPayloadMessage)
                        .toList(),
                new LinkedHashMap<>(providerOptions)
        );
    }

    private ExecutionPreviewPayloadMessageResponse toPayloadMessage(CanonicalMessage message) {
        String text = message.parts().stream()
                .map(part -> part.text() == null ? "" : part.text())
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return new ExecutionPreviewPayloadMessageResponse(
                message.role().name(),
                text,
                message.parts().stream()
                        .map(part -> new ExecutionPreviewPayloadPartResponse(
                                part.type().name(),
                                part.text(),
                                part.mimeType(),
                                part.uri(),
                                part.name(),
                                part.toolCallId(),
                                part.toolName()
                        ))
                        .toList()
        );
    }

    private ExecutionPreviewBindingSummaryResponse buildBindingSummary(RouteCandidateView providerBinding) {
        return new ExecutionPreviewBindingSummaryResponse(
                providerBinding.bindingId(),
                providerBinding.bindingPriority(),
                providerBinding.bindingWeight(),
                providerBinding.capabilityLevel(),
                providerBinding.candidate().siteProfileId(),
                providerBinding.candidate().credentialId(),
                providerBinding.candidate().providerType() == null ? null : providerBinding.candidate().providerType().name(),
                providerBinding.candidate().providerFamily() == null ? null : providerBinding.candidate().providerFamily().name(),
                providerBinding.candidate().siteKind() == null ? null : providerBinding.candidate().siteKind().name(),
                providerBinding.candidate().baseUrl(),
                providerBinding.candidate().modelKey()
        );
    }

    private NormalizedResponsePreviewResponse buildNormalizedPreview(
            com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan plan) {
        java.util.ArrayList<String> notes = new java.util.ArrayList<>();
        if (!plan.blockerReasons().isEmpty()) {
            notes.addAll(plan.blockerReasons());
        } else if (!plan.degradations().isEmpty()) {
            notes.addAll(plan.degradations());
        } else {
            notes.add("Preview 阶段展示的是预期规范化结果轮廓；执行后可看到真实返回与 trace 时间线。");
        }
        return new NormalizedResponsePreviewResponse(
                plan.surface(),
                plan.objectMode(),
                plan.supportStatus() == null ? null : plan.supportStatus().name(),
                plan.degradationLevel() == null ? null : plan.degradationLevel().name(),
                List.copyOf(notes)
        );
    }
}
