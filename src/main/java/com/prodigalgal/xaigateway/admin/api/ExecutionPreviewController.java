package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionService;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.PreparedChatExecution;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import java.util.LinkedHashMap;
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

    public ExecutionPreviewController(
            GatewayRouteSelectionService gatewayRouteSelectionService,
            ProviderExecutionSupportService providerExecutionSupportService) {
        this.gatewayRouteSelectionService = gatewayRouteSelectionService;
        this.providerExecutionSupportService = providerExecutionSupportService;
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
                false
        ));

        ProviderType providerType = selectionResult.selectedCandidate().candidate().providerType();
        return switch (providerType) {
            case OPENAI_DIRECT, OPENAI_COMPATIBLE -> previewOpenAi(selectionResult);
            case ANTHROPIC_DIRECT -> previewAnthropic(selectionResult);
            case GEMINI_DIRECT -> previewGemini(selectionResult);
            case OLLAMA_DIRECT -> new ExecutionPreviewResponse(providerType, selectionResult, Map.of());
        };
    }

    private ExecutionPreviewResponse previewOpenAi(RouteSelectionResult selectionResult) {
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
        return new ExecutionPreviewResponse(prepared.providerType(), selectionResult, options);
    }

    private ExecutionPreviewResponse previewAnthropic(RouteSelectionResult selectionResult) {
        PreparedChatExecution<AnthropicChatOptions> prepared = providerExecutionSupportService.prepareAnthropic(
                selectionResult,
                AnthropicChatOptions.builder().model(selectionResult.resolvedModelKey()).build()
        );
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("model", prepared.options().getModel());
        options.put("cacheOptions", prepared.options().getCacheOptions());
        options.put("metadata", prepared.options().getMetadata());
        return new ExecutionPreviewResponse(prepared.providerType(), selectionResult, options);
    }

    private ExecutionPreviewResponse previewGemini(RouteSelectionResult selectionResult) {
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
        return new ExecutionPreviewResponse(prepared.providerType(), selectionResult, options);
    }
}
