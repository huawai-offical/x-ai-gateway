package com.prodigalgal.xaigateway.gateway.core.execution;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigEnabled;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
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
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileContent;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.provider.adapter.anthropic.AnthropicChatModelFactory;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AnthropicNativeGatewayChatRuntime implements GatewayChatRuntime {

    private static final long DEFAULT_MAX_TOKENS = 4096L;

    private final AnthropicChatModelFactory anthropicChatModelFactory;
    private final GatewayFileService gatewayFileService;
    private final DistributedKeyQueryService distributedKeyQueryService;

    public AnthropicNativeGatewayChatRuntime(
            AnthropicChatModelFactory anthropicChatModelFactory,
            GatewayFileService gatewayFileService,
            DistributedKeyQueryService distributedKeyQueryService) {
        this.anthropicChatModelFactory = anthropicChatModelFactory;
        this.gatewayFileService = gatewayFileService;
        this.distributedKeyQueryService = distributedKeyQueryService;
    }

    @Override
    public ExecutionBackend backend() {
        return ExecutionBackend.NATIVE;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.ANTHROPIC_DIRECT;
    }

    @Override
    public CanonicalResponse execute(GatewayChatRuntimeContext context) {
        AnthropicClient client = anthropicChatModelFactory.createClient(context.credential().getBaseUrl(), context.apiKey());
        try {
            Message response = client.messages().create(buildRequest(context.canonicalRequest(), context.selectionResult().resolvedModelKey()));
            return toCanonicalResponse(context, response);
        } finally {
            client.close();
        }
    }

    @Override
    public Flux<CanonicalStreamEvent> executeStream(GatewayChatRuntimeContext context) {
        return Flux.using(
                () -> anthropicChatModelFactory.createClient(context.credential().getBaseUrl(), context.apiKey()),
                client -> Flux.using(
                        () -> client.messages().createStreaming(buildRequest(context.canonicalRequest(), context.selectionResult().resolvedModelKey())),
                        stream -> Flux.fromStream(stream.stream()).flatMap(this::toStreamEvents),
                        StreamResponse::close
                ),
                AnthropicClient::close
        );
    }

    private MessageCreateParams buildRequest(CanonicalRequest request, String model) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens() == null ? DEFAULT_MAX_TOKENS : request.maxTokens().longValue());
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        String systemPrompt = systemPrompt(request.messages());
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(systemPrompt);
        }
        if (request.reasoning() != null) {
            builder.thinking(ThinkingConfigEnabled.builder().budgetTokens(2048).build());
        }
        if (request.tools() != null) {
            for (CanonicalToolDefinition tool : request.tools()) {
                builder.addTool(Tool.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .strict(Boolean.TRUE.equals(tool.strict()))
                        .build());
            }
        }
        for (CanonicalMessage message : request.messages()) {
            if (message.role() == CanonicalMessageRole.SYSTEM) {
                continue;
            }
            MessageParam messageParam = toMessageParam(request.distributedKeyPrefix(), message);
            if (messageParam != null) {
                builder.addMessage(messageParam);
            }
        }
        return builder.build();
    }

    private MessageParam toMessageParam(String distributedKeyPrefix, CanonicalMessage message) {
        if (message.parts() == null || message.parts().isEmpty()) {
            return null;
        }
        if (message.role() == CanonicalMessageRole.TOOL) {
            CanonicalContentPart toolResult = message.parts().stream()
                    .filter(part -> part.type() == CanonicalPartType.TOOL_RESULT)
                    .findFirst()
                    .orElse(null);
            if (toolResult == null) {
                return null;
            }
            return MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(List.of(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                            .toolUseId(toolResult.toolCallId() == null ? "tool-use" : toolResult.toolCallId())
                            .content(toolResult.text() == null ? "" : toolResult.text())
                            .build())))
                    .build();
        }

        List<ContentBlockParam> blocks = new ArrayList<>();
        for (CanonicalContentPart part : message.parts()) {
            switch (part.type()) {
                case TEXT -> {
                    if (part.text() != null && !part.text().isBlank()) {
                        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(part.text()).build()));
                    }
                }
                case IMAGE -> blocks.add(ContentBlockParam.ofImage(toImageBlock(distributedKeyPrefix, part)));
                case FILE -> blocks.add(ContentBlockParam.ofDocument(toDocumentBlock(distributedKeyPrefix, part)));
                case TOOL_RESULT -> {
                }
            }
        }
        if (blocks.isEmpty()) {
            return null;
        }
        return MessageParam.builder()
                .role(message.role() == CanonicalMessageRole.ASSISTANT ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER)
                .contentOfBlockParams(blocks)
                .build();
    }

    private ImageBlockParam toImageBlock(String distributedKeyPrefix, CanonicalContentPart part) {
        if (part.uri() != null && part.uri().startsWith("gateway://")) {
            GatewayFileContent content = resolveGatewayFile(distributedKeyPrefix, part);
            return ImageBlockParam.builder()
                    .urlSource("data:" + content.mimeType() + ";base64," + Base64.getEncoder().encodeToString(content.bytes()))
                    .build();
        }
        return ImageBlockParam.builder()
                .urlSource(part.uri())
                .build();
    }

    private DocumentBlockParam toDocumentBlock(String distributedKeyPrefix, CanonicalContentPart part) {
        if (part.uri() != null && part.uri().startsWith("gateway://")) {
            GatewayFileContent content = resolveGatewayFile(distributedKeyPrefix, part);
            return DocumentBlockParam.builder()
                    .base64Source(Base64.getEncoder().encodeToString(content.bytes()))
                    .title(part.name())
                    .build();
        }
        return DocumentBlockParam.builder()
                .urlSource(part.uri())
                .title(part.name())
                .build();
    }

    private GatewayFileContent resolveGatewayFile(String distributedKeyPrefix, CanonicalContentPart part) {
        return gatewayFileService.getFileContent(
                part.uri().substring("gateway://".length()),
                distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                        .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                        .id()
        );
    }

    private String systemPrompt(List<CanonicalMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == CanonicalMessageRole.SYSTEM)
                .flatMap(message -> message.parts().stream())
                .filter(part -> part.type() == CanonicalPartType.TEXT)
                .map(CanonicalContentPart::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private CanonicalResponse toCanonicalResponse(GatewayChatRuntimeContext context, Message response) {
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<CanonicalToolCall> toolCalls = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            if (block.isText()) {
                text.append(block.asText().text());
                continue;
            }
            if (block.isThinking()) {
                reasoning.append(block.asThinking().thinking());
                continue;
            }
            if (block.isToolUse()) {
                toolCalls.add(new CanonicalToolCall(
                        block.asToolUse().id(),
                        "function",
                        block.asToolUse().name(),
                        block.asToolUse()._input().toString()
                ));
            }
        }
        return new CanonicalResponse(
                null,
                context.selectionResult().publicModel(),
                text.isEmpty() ? null : text.toString(),
                reasoning.isEmpty() ? null : reasoning.toString(),
                List.copyOf(toolCalls),
                toUsage(response.usage()),
                response.stopReason().isPresent()
                        ? GatewayFinishReason.fromRaw(response.stopReason().get().toString())
                        : null
        );
    }

    private Flux<CanonicalStreamEvent> toStreamEvents(RawMessageStreamEvent event) {
        List<CanonicalStreamEvent> events = new ArrayList<>();
        if (event.isContentBlockStart() && event.asContentBlockStart().contentBlock().isToolUse()) {
            var toolUse = event.asContentBlockStart().contentBlock().asToolUse();
            events.add(new CanonicalStreamEvent(
                    CanonicalStreamEventType.TOOL_CALLS,
                    null,
                    null,
                    List.of(new CanonicalToolCall(toolUse.id(), "function", toolUse.name(), toolUse._input().toString())),
                    CanonicalUsage.empty(),
                    false,
                    null,
                    null,
                    null
            ));
        }
        if (event.isContentBlockDelta()) {
            if (event.asContentBlockDelta().delta().isText()) {
                String delta = event.asContentBlockDelta().delta().asText().text();
                if (delta != null && !delta.isBlank()) {
                    events.add(new CanonicalStreamEvent(
                            CanonicalStreamEventType.TEXT_DELTA,
                            delta,
                            null,
                            List.of(),
                            CanonicalUsage.empty(),
                            false,
                            null,
                            null,
                            null
                    ));
                }
            }
            if (event.asContentBlockDelta().delta().isThinking()) {
                String delta = event.asContentBlockDelta().delta().asThinking().thinking();
                if (delta != null && !delta.isBlank()) {
                    events.add(new CanonicalStreamEvent(
                            CanonicalStreamEventType.REASONING_DELTA,
                            null,
                            delta,
                            List.of(),
                            CanonicalUsage.empty(),
                            false,
                            null,
                            null,
                            null
                    ));
                }
            }
        }
        if (event.isMessageDelta()) {
            MessageDeltaUsage usage = event.asMessageDelta().usage();
            events.add(new CanonicalStreamEvent(
                    CanonicalStreamEventType.COMPLETED,
                    null,
                    null,
                    List.of(),
                    toUsage(usage),
                    true,
                    event.asMessageDelta().delta().stopReason().isPresent()
                            ? GatewayFinishReason.fromRaw(event.asMessageDelta().delta().stopReason().get().toString())
                            : null,
                    null,
                    null
            ));
        }
        return Flux.fromIterable(events);
    }

    private CanonicalUsage toUsage(com.anthropic.models.messages.Usage usage) {
        if (usage == null) {
            return CanonicalUsage.empty();
        }
        int promptTokens = (int) usage.inputTokens();
        int completionTokens = (int) usage.outputTokens();
        int cacheHitTokens = usage.cacheReadInputTokens().orElse(0L).intValue();
        int cacheWriteTokens = usage.cacheCreationInputTokens().orElse(0L).intValue();
        return new CanonicalUsage(
                true,
                promptTokens,
                completionTokens,
                promptTokens + completionTokens,
                cacheHitTokens,
                cacheWriteTokens,
                0
        );
    }

    private CanonicalUsage toUsage(MessageDeltaUsage usage) {
        if (usage == null) {
            return CanonicalUsage.empty();
        }
        int promptTokens = usage.inputTokens().orElse(0L).intValue();
        int completionTokens = (int) usage.outputTokens();
        int cacheHitTokens = usage.cacheReadInputTokens().orElse(0L).intValue();
        int cacheWriteTokens = usage.cacheCreationInputTokens().orElse(0L).intValue();
        return new CanonicalUsage(
                true,
                promptTokens,
                completionTokens,
                promptTokens + completionTokens,
                cacheHitTokens,
                cacheWriteTokens,
                0
        );
    }
}
