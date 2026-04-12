package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResource;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import java.net.URI;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
public class GatewayChatPromptBuilder {

    private final DistributedKeyQueryService distributedKeyQueryService;
    private final GatewayFileService gatewayFileService;

    public GatewayChatPromptBuilder(
            DistributedKeyQueryService distributedKeyQueryService,
            GatewayFileService gatewayFileService) {
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.gatewayFileService = gatewayFileService;
    }

    public Prompt buildPrompt(Object options, ChatExecutionRequest request) {
        List<Message> messages = request.messages().stream()
                .filter(this::isUsableMessage)
                .map(message -> toPromptMessage(request.distributedKeyPrefix(), message))
                .toList();
        return new Prompt(messages, (ChatOptions) options);
    }

    private Message toPromptMessage(String distributedKeyPrefix, ChatExecutionRequest.MessageInput message) {
        return switch (message.role().trim().toLowerCase()) {
            case "system" -> new SystemMessage(message.content() == null ? "" : message.content().trim());
            case "assistant", "model" -> new org.springframework.ai.chat.messages.AssistantMessage(message.content() == null ? "" : message.content().trim());
            case "tool" -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            message.toolCallId() == null ? "tool-call" : message.toolCallId(),
                            message.toolName() == null ? "tool" : message.toolName(),
                            message.content() == null ? "" : message.content().trim()
                    )))
                    .build();
            default -> {
                if (message.media() != null && !message.media().isEmpty()) {
                    List<Media> media = message.media().stream()
                            .filter(item -> item.url() != null && !item.url().isBlank())
                            .map(item -> toMedia(distributedKeyPrefix, item))
                            .toList();
                    yield UserMessage.builder()
                            .text(message.content() == null ? "" : message.content().trim())
                            .media(media)
                            .build();
                }
                yield new UserMessage(message.content() == null ? "" : message.content().trim());
            }
        };
    }

    private boolean isUsableMessage(ChatExecutionRequest.MessageInput message) {
        boolean hasText = message.content() != null && !message.content().isBlank();
        boolean hasMedia = message.media() != null && !message.media().isEmpty();
        return hasText || hasMedia;
    }

    private Media toMedia(String distributedKeyPrefix, ChatExecutionRequest.MediaInput item) {
        if (item.url() != null && item.url().startsWith("gateway://")) {
            String fileKey = item.url().substring("gateway://".length());
            Long distributedKeyId = distributedKeyQueryService.findActiveByKeyPrefix(distributedKeyPrefix)
                    .orElseThrow(() -> new IllegalArgumentException("未找到可用的 DistributedKey。"))
                    .id();
            GatewayFileResource resource = gatewayFileService.resolveFileResource(fileKey, distributedKeyId);
            return Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(resource.mimeType()))
                    .data(resource.resource())
                    .name(resource.filename())
                    .build();
        }

        return Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(item.mimeType() == null || item.mimeType().isBlank()
                        ? ("file".equalsIgnoreCase(item.kind()) ? "application/octet-stream" : "image/*")
                        : item.mimeType()))
                .data(URI.create(item.url()))
                .name(item.name())
                .build();
    }
}
