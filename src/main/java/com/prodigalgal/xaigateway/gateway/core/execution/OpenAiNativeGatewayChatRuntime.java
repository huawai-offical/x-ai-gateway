package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEvent;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalStreamEventType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportService;
import com.prodigalgal.xaigateway.provider.adapter.openai.OpenAiChatModelFactory;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OpenAiNativeGatewayChatRuntime implements GatewayChatRuntime {

    private final OpenAiChatModelFactory openAiChatModelFactory;
    private final ProviderExecutionSupportService providerExecutionSupportService;
    private final GatewayFileService gatewayFileService;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final ObjectMapper objectMapper;

    public OpenAiNativeGatewayChatRuntime(
            OpenAiChatModelFactory openAiChatModelFactory,
            ProviderExecutionSupportService providerExecutionSupportService,
            GatewayFileService gatewayFileService,
            DistributedKeyQueryService distributedKeyQueryService,
            ObjectMapper objectMapper) {
        this.openAiChatModelFactory = openAiChatModelFactory;
        this.providerExecutionSupportService = providerExecutionSupportService;
        this.gatewayFileService = gatewayFileService;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.OPENAI_DIRECT || candidate.providerType() == ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public CanonicalResponse execute(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        OpenAiApi api = openAiChatModelFactory.createApi(context.credential().getBaseUrl(), context.apiKey());
        OpenAiApi.ChatCompletion response = api.chatCompletionEntity(buildRequest(request, context.selectionResult().resolvedModelKey(), false))
                .getBody();
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI native 响应为空。");
        }
        OpenAiApi.ChatCompletion.Choice choice = response.choices().getFirst();
        OpenAiApi.ChatCompletionMessage message = choice.message();
        return new CanonicalResponse(
                null,
                context.selectionResult().publicModel(),
                message == null ? null : message.content(),
                message == null ? null : message.reasoningContent(),
                toolCalls(message == null ? List.of() : message.toolCalls()),
                toUsage(response.usage()),
                com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.fromRaw(
                        choice.finishReason() == null ? null : choice.finishReason().toString()
                )
        );
    }

    @Override
    public Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
        CanonicalRequest request = context.canonicalRequest();
        OpenAiApi api = openAiChatModelFactory.createApi(context.credential().getBaseUrl(), context.apiKey());
        return api.chatCompletionStream(buildRequest(request, context.selectionResult().resolvedModelKey(), true))
                .flatMap(chunk -> {
                    if (chunk == null || chunk.choices() == null || chunk.choices().isEmpty()) {
                        return Flux.empty();
                    }
                    OpenAiApi.ChatCompletionChunk.ChunkChoice choice = chunk.choices().getFirst();
                    OpenAiApi.ChatCompletionMessage delta = choice.delta();
                    List<CanonicalStreamEvent> events = new ArrayList<>();
                    if (delta != null && delta.content() != null && !delta.content().isBlank()) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.TEXT_DELTA,
                                delta.content(),
                                null,
                                List.of(),
                                CanonicalUsage.empty(),
                                false,
                                null,
                                null,
                                null
                        ));
                    }
                    if (delta != null && delta.reasoningContent() != null && !delta.reasoningContent().isBlank()) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.REASONING_DELTA,
                                null,
                                delta.reasoningContent(),
                                List.of(),
                                CanonicalUsage.empty(),
                                false,
                                null,
                                null,
                                null
                        ));
                    }
                    if (delta != null && delta.toolCalls() != null && !delta.toolCalls().isEmpty()) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.TOOL_CALLS,
                                null,
                                null,
                                toolCalls(delta.toolCalls()),
                                CanonicalUsage.empty(),
                                false,
                                null,
                                null,
                                null
                        ));
                    }
                    if (choice.finishReason() != null) {
                        events.add(new CanonicalStreamEvent(
                                CanonicalStreamEventType.COMPLETED,
                                null,
                                null,
                                List.of(),
                                toUsage(chunk.usage()),
                                true,
                                com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason.fromRaw(choice.finishReason().toString()),
                                delta == null ? null : delta.content(),
                                delta == null ? null : delta.reasoningContent()
                        ));
                    }
                    return Flux.fromIterable(events);
                });
    }

    private OpenAiApi.ChatCompletionRequest buildRequest(CanonicalRequest request, String model, boolean stream) {
        List<OpenAiApi.ChatCompletionMessage> messages = request.messages().stream()
                .map(message -> toMessage(request.distributedKeyPrefix(), message))
                .toList();
        List<OpenAiApi.FunctionTool> tools = request.tools() == null
                ? List.of()
                : request.tools().stream().map(this::toTool).toList();
        OpenAiApi.ChatCompletionRequest chatCompletionRequest = new OpenAiApi.ChatCompletionRequest(
                messages,
                model,
                null,
                null,
                null,
                null,
                null,
                null,
                request.maxTokens(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                stream,
                stream ? OpenAiApi.ChatCompletionRequest.StreamOptions.INCLUDE_USAGE : null,
                request.temperature(),
                null,
                tools,
                request.toolChoice(),
                null,
                null,
                request.reasoning() == null ? null : request.reasoning().effort(),
                null,
                null,
                null,
                null,
                Map.of()
        );
        return stream ? chatCompletionRequest.streamOptions(OpenAiApi.ChatCompletionRequest.StreamOptions.INCLUDE_USAGE) : chatCompletionRequest;
    }

    private OpenAiApi.ChatCompletionMessage toMessage(String distributedKeyPrefix, CanonicalMessage message) {
        if (message.role() == CanonicalMessageRole.TOOL) {
            CanonicalContentPart toolResult = message.parts().stream()
                    .filter(part -> part.type() == CanonicalPartType.TOOL_RESULT)
                    .findFirst()
                    .orElse(null);
            return new OpenAiApi.ChatCompletionMessage(
                    toolResult == null ? "" : toolResult.text(),
                    OpenAiApi.ChatCompletionMessage.Role.TOOL,
                    null,
                    toolResult == null ? null : toolResult.toolCallId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        List<CanonicalContentPart> mediaParts = message.parts() == null
                ? List.of()
                : message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.IMAGE || part.type() == CanonicalPartType.FILE)
                .toList();
        String text = message.parts() == null
                ? ""
                : message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TEXT)
                .map(CanonicalContentPart::text)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (mediaParts.isEmpty()) {
            return new OpenAiApi.ChatCompletionMessage(text, role(message.role()));
        }
        List<OpenAiApi.ChatCompletionMessage.MediaContent> rawContent = new ArrayList<>();
        if (!text.isBlank()) {
            rawContent.add(new OpenAiApi.ChatCompletionMessage.MediaContent(text));
        }
        for (CanonicalContentPart part : mediaParts) {
            rawContent.add(toMediaContent(distributedKeyPrefix, part));
        }
        return new OpenAiApi.ChatCompletionMessage(rawContent, role(message.role()));
    }

    private OpenAiApi.ChatCompletionMessage.MediaContent toMediaContent(String distributedKeyPrefix, CanonicalContentPart part) {
        if (part.type() == CanonicalPartType.FILE) {
            GatewayFileContent content = resolveGatewayFile(distributedKeyPrefix, part);
            return new OpenAiApi.ChatCompletionMessage.MediaContent(
                    new OpenAiApi.ChatCompletionMessage.MediaContent.InputFile(
                            content.metadata().filename(),
                            Base64.getEncoder().encodeToString(content.bytes())
                    )
            );
        }
        if (part.uri() != null && part.uri().startsWith("gateway://")) {
            GatewayFileContent content = resolveGatewayFile(distributedKeyPrefix, part);
            String dataUrl = "data:" + content.mimeType() + ";base64," + Base64.getEncoder().encodeToString(content.bytes());
            return new OpenAiApi.ChatCompletionMessage.MediaContent(
                    new OpenAiApi.ChatCompletionMessage.MediaContent.ImageUrl(dataUrl)
            );
        }
        return new OpenAiApi.ChatCompletionMessage.MediaContent(
                new OpenAiApi.ChatCompletionMessage.MediaContent.ImageUrl(part.uri())
        );
    }

    private GatewayFileContent resolveGatewayFile(String distributedKeyPrefix, CanonicalContentPart part) {
        if (part.uri() == null || !part.uri().startsWith("gateway://")) {
            throw new IllegalArgumentException("当前 native OpenAI 仅支持 gateway:// 文件解析。");
        }
        return gatewayFileService.getFileContent(
                part.uri().substring("gateway://".length()),
                distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                        .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                        .id()
        );
    }

    private OpenAiApi.FunctionTool toTool(CanonicalToolDefinition tool) {
        OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
                tool.name(),
                tool.description(),
                tool.inputSchema() == null ? Map.of("type", "object") : objectMapper.convertValue(tool.inputSchema(), Map.class),
                tool.strict()
        );
        return new OpenAiApi.FunctionTool(function);
    }

    private List<CanonicalToolCall> toolCalls(List<OpenAiApi.ChatCompletionMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .map(toolCall -> new CanonicalToolCall(
                        toolCall.id(),
                        toolCall.type(),
                        toolCall.function() == null ? null : toolCall.function().name(),
                        toolCall.function() == null ? null : toolCall.function().arguments()
                ))
                .toList();
    }

    private CanonicalUsage toUsage(OpenAiApi.Usage usage) {
        if (usage == null) {
            return CanonicalUsage.empty();
        }
        int promptTokens = usage.promptTokens() == null ? 0 : usage.promptTokens();
        int completionTokens = usage.completionTokens() == null ? 0 : usage.completionTokens();
        int totalTokens = usage.totalTokens() == null ? promptTokens + completionTokens : usage.totalTokens();
        int cacheHitTokens = usage.promptTokensDetails() == null || usage.promptTokensDetails().cachedTokens() == null
                ? 0
                : usage.promptTokensDetails().cachedTokens();
        int reasoningTokens = usage.completionTokenDetails() == null || usage.completionTokenDetails().reasoningTokens() == null
                ? 0
                : usage.completionTokenDetails().reasoningTokens();
        return new CanonicalUsage(
                true,
                promptTokens,
                completionTokens,
                totalTokens,
                cacheHitTokens,
                0,
                reasoningTokens
        );
    }

    private OpenAiApi.ChatCompletionMessage.Role role(CanonicalMessageRole role) {
        if (role == null) {
            return OpenAiApi.ChatCompletionMessage.Role.USER;
        }
        return switch (role) {
            case SYSTEM -> OpenAiApi.ChatCompletionMessage.Role.SYSTEM;
            case USER -> OpenAiApi.ChatCompletionMessage.Role.USER;
            case ASSISTANT -> OpenAiApi.ChatCompletionMessage.Role.ASSISTANT;
            case TOOL -> OpenAiApi.ChatCompletionMessage.Role.TOOL;
        };
    }
}
