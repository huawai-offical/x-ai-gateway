package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.provider.adapter.SpringAiToolCallbackFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class GeminiOptionsMapper {

    private final GeminiCachedContentReferenceService cachedContentReferenceService;
    private final SpringAiToolCallbackFactory springAiToolCallbackFactory;

    public GeminiOptionsMapper(
            GeminiCachedContentReferenceService cachedContentReferenceService,
            SpringAiToolCallbackFactory springAiToolCallbackFactory) {
        this.cachedContentReferenceService = cachedContentReferenceService;
        this.springAiToolCallbackFactory = springAiToolCallbackFactory;
    }

    public GoogleGenAiChatOptions applyCacheOptions(
            GoogleGenAiChatOptions baseOptions,
            RouteSelectionResult selectionResult,
            List<GatewayToolDefinition> tools) {
        GoogleGenAiChatOptions source = baseOptions == null
                ? GoogleGenAiChatOptions.builder().build()
                : GoogleGenAiChatOptions.fromOptions(baseOptions);

        GoogleGenAiChatOptions.Builder builder = GoogleGenAiChatOptions.builder()
                .model(source.getModel())
                .temperature(source.getTemperature())
                .topP(source.getTopP())
                .topK(source.getTopK())
                .maxOutputTokens(source.getMaxOutputTokens())
                .candidateCount(source.getCandidateCount())
                .thinkingBudget(source.getThinkingBudget())
                .includeThoughts(source.getIncludeThoughts())
                .thinkingLevel(source.getThinkingLevel())
                .internalToolExecutionEnabled(false)
                .includeExtendedUsageMetadata(Boolean.TRUE)
                .autoCacheThreshold(source.getAutoCacheThreshold() == null ? 1024 : source.getAutoCacheThreshold())
                .autoCacheTtl(source.getAutoCacheTtl() == null ? Duration.ofHours(1) : source.getAutoCacheTtl());

        Map<String, String> labels = new LinkedHashMap<>();
        if (source.getLabels() != null) {
            labels.putAll(source.getLabels());
        }
        labels.put("gateway.distributed_key_id", String.valueOf(selectionResult.distributedKeyId()));
        labels.put("gateway.model_group", selectionResult.modelGroup());
        labels.put("gateway.selection_source", selectionResult.selectionSource().name());
        builder.labels(labels);

        if (tools != null && !tools.isEmpty()) {
            builder.toolCallbacks(springAiToolCallbackFactory.toCallbacks(tools));
        }

        cachedContentReferenceService.find(
                        selectionResult.distributedKeyId(),
                        selectionResult.modelGroup(),
                        selectionResult.prefixHash()
                )
                .filter(reference -> reference.credentialId().equals(selectionResult.selectedCandidate().candidate().credentialId()))
                .ifPresent(reference -> {
                    builder.useCachedContent(true);
                    builder.cachedContentName(reference.cachedContentName());
                });

        return builder.build();
    }
}
