package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.PreparedChatExecution;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiChatModelFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class GeminiGatewayChatRuntime implements GatewayChatRuntime {

    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final GeminiChatModelFactory geminiChatModelFactory;
    private final GatewayChatPromptBuilder gatewayChatPromptBuilder;

    public GeminiGatewayChatRuntime(
            ProviderExecutionSupportService providerExecutionSupportService,
            GeminiChatModelFactory geminiChatModelFactory,
            GatewayChatPromptBuilder gatewayChatPromptBuilder) {
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.geminiChatModelFactory = geminiChatModelFactory;
        this.gatewayChatPromptBuilder = gatewayChatPromptBuilder;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.GEMINI_DIRECT;
    }

    @Override
    public GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context) {
        GoogleGenAiChatOptions baseOptions = GoogleGenAiChatOptions.builder()
                .model(context.selectionResult().resolvedModelKey())
                .temperature(context.request().temperature())
                .maxOutputTokens(context.request().maxTokens())
                .build();
        PreparedChatExecution<GoogleGenAiChatOptions> prepared = providerExecutionSupportService.prepareGemini(
                context.selectionResult(),
                baseOptions,
                context.request().tools()
        );
        GoogleGenAiChatModel model = geminiChatModelFactory.create(context.credential().getBaseUrl(), context.apiKey(), prepared.options());
        try {
            ChatResponse response = model.call(gatewayChatPromptBuilder.buildPrompt(prepared.options(), context.request()));
            return new GatewayChatRuntimeResult(
                    response.getResult().getOutput().getText(),
                    providerExecutionSupportService.normalizeUsage(context.selectionResult(), response.getMetadata().getUsage()),
                    response.getResult().getOutput().getToolCalls().stream()
                            .map(toolCall -> new GatewayToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                            .toList()
            );
        } finally {
            close(model);
        }
    }

    @Override
    public Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context) {
        GoogleGenAiChatOptions baseOptions = GoogleGenAiChatOptions.builder()
                .model(context.selectionResult().resolvedModelKey())
                .temperature(context.request().temperature())
                .maxOutputTokens(context.request().maxTokens())
                .build();
        PreparedChatExecution<GoogleGenAiChatOptions> prepared = providerExecutionSupportService.prepareGemini(
                context.selectionResult(),
                baseOptions,
                context.request().tools()
        );
        GoogleGenAiChatModel model = geminiChatModelFactory.create(context.credential().getBaseUrl(), context.apiKey(), prepared.options());
        return model.stream(gatewayChatPromptBuilder.buildPrompt(prepared.options(), context.request()))
                .map(response -> new ChatExecutionStreamChunk(
                        response.getResult().getOutput().getText(),
                        null,
                        providerExecutionSupportService.normalizeUsage(context.selectionResult(), response.getMetadata().getUsage()),
                        false,
                        response.getResult().getOutput().getToolCalls().stream()
                                .map(toolCall -> new GatewayToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                                .toList()
                ))
                .concatWithValues(new ChatExecutionStreamChunk(null, "stop", com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage.empty(), true))
                .doFinally(signalType -> close(model));
    }

    private void close(Object model) {
        if (model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                return;
            } catch (Exception ignored) {
            }
        }
        if (model instanceof org.springframework.beans.factory.DisposableBean disposableBean) {
            try {
                disposableBean.destroy();
            } catch (Exception ignored) {
            }
        }
    }
}
