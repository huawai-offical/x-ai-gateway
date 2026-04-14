package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.PreparedChatExecution;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import java.util.List;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AnthropicGatewayChatRuntime implements GatewayChatRuntime {

    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final AnthropicChatModelFactory anthropicChatModelFactory;
    private final GatewayChatPromptBuilder gatewayChatPromptBuilder;

    public AnthropicGatewayChatRuntime(
            ProviderExecutionSupportService providerExecutionSupportService,
            AnthropicChatModelFactory anthropicChatModelFactory,
            GatewayChatPromptBuilder gatewayChatPromptBuilder) {
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.anthropicChatModelFactory = anthropicChatModelFactory;
        this.gatewayChatPromptBuilder = gatewayChatPromptBuilder;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.ANTHROPIC_DIRECT;
    }

    @Override
    public CanonicalResponse execute(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        AnthropicChatOptions baseOptions = AnthropicChatOptions.builder()
                .model(context.selectionResult().resolvedModelKey())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();
        PreparedChatExecution<AnthropicChatOptions> prepared = providerExecutionSupportService.prepareAnthropic(
                context.selectionResult(),
                baseOptions,
                toGatewayTools(request),
                request.toolChoice()
        );
        AnthropicChatModel model = anthropicChatModelFactory.create(context.credential().getBaseUrl(), context.apiKey(), prepared.options());
        try {
            ChatResponse response = model.call(gatewayChatPromptBuilder.buildPrompt(prepared.options(), request));
            return new CanonicalResponse(
                    null,
                    context.selectionResult().publicModel(),
                    response.getResult().getOutput().getText(),
                    null,
                    response.getResult().getOutput().getToolCalls().stream()
                            .map(toolCall -> new CanonicalToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                            .toList(),
                    toUsage(providerExecutionSupportService.normalizeUsage(context.selectionResult(), response.getMetadata().getUsage())),
                    com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.fromRaw(finishReason(response))
            );
        } finally {
            close(model);
        }
    }

    @Override
    public Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        AnthropicChatOptions baseOptions = AnthropicChatOptions.builder()
                .model(context.selectionResult().resolvedModelKey())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();
        PreparedChatExecution<AnthropicChatOptions> prepared = providerExecutionSupportService.prepareAnthropic(
                context.selectionResult(),
                baseOptions,
                toGatewayTools(request),
                request.toolChoice()
        );
        AnthropicChatModel model = anthropicChatModelFactory.create(context.credential().getBaseUrl(), context.apiKey(), prepared.options());
        return model.stream(gatewayChatPromptBuilder.buildPrompt(prepared.options(), request))
                .map(response -> new CanonicalStreamEvent(
                        isTerminal(response) ? CanonicalStreamEventType.COMPLETED
                                : !response.getResult().getOutput().getToolCalls().isEmpty()
                                ? CanonicalStreamEventType.TOOL_CALLS
                                : CanonicalStreamEventType.TEXT_DELTA,
                        response.getResult().getOutput().getText(),
                        null,
                        response.getResult().getOutput().getToolCalls().stream()
                                .map(toolCall -> new CanonicalToolCall(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments()))
                                .toList(),
                        toUsage(providerExecutionSupportService.normalizeUsage(context.selectionResult(), response.getMetadata().getUsage())),
                        isTerminal(response),
                        com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.fromRaw(finishReason(response)),
                        isTerminal(response) ? response.getResult().getOutput().getText() : null,
                        null
                ))
                .doFinally(signalType -> close(model));
    }

    private List<GatewayToolDefinition> toGatewayTools(CanonicalRequest request) {
        if (request.tools() == null || request.tools().isEmpty()) {
            return List.of();
        }
        return request.tools().stream()
                .map(tool -> new GatewayToolDefinition(tool.name(), tool.description(), tool.inputSchema(), tool.strict()))
                .toList();
    }

    private CanonicalUsage toUsage(com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage usage) {
        if (usage == null || usage.isEmpty()) {
            return CanonicalUsage.empty();
        }
        return new CanonicalUsage(
                true,
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                usage.reasoningTokens()
        );
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
