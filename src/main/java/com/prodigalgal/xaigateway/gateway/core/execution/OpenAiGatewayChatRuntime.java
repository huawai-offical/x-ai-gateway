package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.PreparedChatExecution;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiChatModelFactory;
import java.util.List;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OpenAiGatewayChatRuntime implements GatewayChatRuntime {

    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final OpenAiChatModelFactory openAiChatModelFactory;
    private final GatewayChatPromptBuilder gatewayChatPromptBuilder;

    public OpenAiGatewayChatRuntime(
            ProviderExecutionSupportService providerExecutionSupportService,
            OpenAiChatModelFactory openAiChatModelFactory,
            GatewayChatPromptBuilder gatewayChatPromptBuilder) {
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.openAiChatModelFactory = openAiChatModelFactory;
        this.gatewayChatPromptBuilder = gatewayChatPromptBuilder;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.OPENAI_DIRECT || candidate.providerType() == ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context) {
        OpenAiChatOptions baseOptions = OpenAiChatOptions.builder()
                .model(context.selectionResult().resolvedModelKey())
                .temperature(context.request().temperature())
                .maxTokens(context.request().maxTokens())
                .build();
        PreparedChatExecution<OpenAiChatOptions> prepared = providerExecutionSupportService.prepareOpenAi(
                context.selectionResult(),
                baseOptions,
                context.request().tools(),
                context.request().toolChoice()
        );
        OpenAiChatModel model = openAiChatModelFactory.create(context.credential().getBaseUrl(), context.apiKey(), prepared.options());
        try {
            ChatResponse response = model.call(gatewayChatPromptBuilder.buildPrompt(prepared.options(), context.request()));
            return new GatewayChatRuntimeResult(
                    response.getResult().getOutput().getText(),
                    providerExecutionSupportService.normalizeUsage(context.selectionResult(), response.getMetadata().getUsage()),
                    response.getResult().getOutput().getToolCalls().stream()
                            .map(toolCall -> new GatewayToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                            .toList(),
                    finishReason(response)
            );
        } finally {
            close(model);
        }
    }

    @Override
    public Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context) {
        OpenAiChatOptions baseOptions = OpenAiChatOptions.builder()
                .model(context.selectionResult().resolvedModelKey())
                .temperature(context.request().temperature())
                .maxTokens(context.request().maxTokens())
                .streamUsage(true)
                .build();
        PreparedChatExecution<OpenAiChatOptions> prepared = providerExecutionSupportService.prepareOpenAi(
                context.selectionResult(),
                baseOptions,
                context.request().tools(),
                context.request().toolChoice()
        );
        OpenAiChatModel model = openAiChatModelFactory.create(context.credential().getBaseUrl(), context.apiKey(), prepared.options());
        return model.stream(gatewayChatPromptBuilder.buildPrompt(prepared.options(), context.request()))
                .map(response -> new ChatExecutionStreamChunk(
                        response.getResult().getOutput().getText(),
                        finishReason(response),
                        providerExecutionSupportService.normalizeUsage(context.selectionResult(), response.getMetadata().getUsage()),
                        isTerminal(response),
                        response.getResult().getOutput().getToolCalls().stream()
                                .map(toolCall -> new GatewayToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                                .toList()
                ))
                .doFinally(signalType -> close(model));
    }

    private boolean isTerminal(ChatResponse response) {
        return finishReason(response) != null && !finishReason(response).isBlank();
    }

    private String finishReason(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        ChatGenerationMetadata metadata = response.getResult().getMetadata();
        return metadata == null ? null : metadata.getFinishReason();
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
