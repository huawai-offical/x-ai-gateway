package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryService;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalChatMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalPartType;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileResource;
import com.prodigalgal.xaigateway.gateway.core.file.GatewayFileService;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
public class GatewayChatPromptBuilder {

    private final CanonicalChatMapper canonicalChatMapper;
    private final DistributedKeyQueryService distributedKeyQueryService;
    private final GatewayFileService gatewayFileService;

    @Autowired
    public GatewayChatPromptBuilder(
            CanonicalChatMapper canonicalChatMapper,
            DistributedKeyQueryService distributedKeyQueryService,
            GatewayFileService gatewayFileService) {
        this.canonicalChatMapper = canonicalChatMapper;
        this.distributedKeyQueryService = distributedKeyQueryService;
        this.gatewayFileService = gatewayFileService;
    }

    public GatewayChatPromptBuilder(
            DistributedKeyQueryService distributedKeyQueryService,
            GatewayFileService gatewayFileService) {
        this(new CanonicalChatMapper(new tools.jackson.databind.ObjectMapper()), distributedKeyQueryService, gatewayFileService);
    }

    public Prompt buildPrompt(Object options, CanonicalRequest canonicalRequest) {
        List<Message> messages = canonicalRequest.messages().stream()
                .filter(this::isUsableMessage)
                .map(message -> toPromptMessage(canonicalRequest.distributedKeyPrefix(), message))
                .toList();
        return new Prompt(messages, (ChatOptions) options);
    }

    public Prompt buildPrompt(Object options, ChatExecutionRequest request) {
        return buildPrompt(options, canonicalChatMapper.toCanonicalRequest(request));
    }

    private Message toPromptMessage(String distributedKeyPrefix, CanonicalMessage message) {
        CanonicalMessageRole role = message.role() == null ? CanonicalMessageRole.USER : message.role();
        String text = joinText(message);
        return switch (role) {
            case SYSTEM -> new SystemMessage(text);
            case ASSISTANT -> new org.springframework.ai.chat.messages.AssistantMessage(text);
            case TOOL -> ToolResponseMessage.builder()
                    .responses(toolResponses(message))
                    .build();
            case USER -> {
                List<Media> media = toMediaList(distributedKeyPrefix, message);
                if (!media.isEmpty()) {
                    yield UserMessage.builder()
                            .text(text)
                            .media(media)
                            .build();
                }
                yield new UserMessage(text);
            }
        };
    }

    private boolean isUsableMessage(CanonicalMessage message) {
        return message != null && message.parts() != null && !message.parts().isEmpty();
    }

    private List<ToolResponseMessage.ToolResponse> toolResponses(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TOOL_RESULT)
                .map(part -> new ToolResponseMessage.ToolResponse(
                        part.toolCallId() == null ? "tool-call" : part.toolCallId(),
                        part.toolName() == null ? "tool" : part.toolName(),
                        part.text() == null ? "" : part.text().trim()
                ))
                .toList();
    }

    private String joinText(CanonicalMessage message) {
        return message.parts().stream()
                .filter(part -> part.type() == CanonicalPartType.TEXT)
                .map(CanonicalContentPart::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private List<Media> toMediaList(String distributedKeyPrefix, CanonicalMessage message) {
        List<Media> media = new ArrayList<>();
        for (CanonicalContentPart part : message.parts()) {
            if (part.type() != CanonicalPartType.IMAGE && part.type() != CanonicalPartType.FILE) {
                continue;
            }
            if (part.uri() == null || part.uri().isBlank()) {
                continue;
            }
            media.add(toMedia(distributedKeyPrefix, part));
        }
        return List.copyOf(media);
    }

    private Media toMedia(String distributedKeyPrefix, CanonicalContentPart item) {
        if (item.uri() != null && item.uri().startsWith("gateway://")) {
            String fileKey = item.uri().substring("gateway://".length());
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
                        ? (item.type() == CanonicalPartType.FILE ? "application/octet-stream" : "image/*")
                        : item.mimeType()))
                .data(URI.create(item.uri()))
                .name(item.name())
                .build();
    }

    @SuppressWarnings("unused")
    private Media toMedia(String distributedKeyPrefix, ChatExecutionRequest.MediaInput item) {
        CanonicalContentPart part = "file".equalsIgnoreCase(item.kind())
                ? CanonicalContentPart.file(item.mimeType(), item.url(), item.name())
                : CanonicalContentPart.image(item.mimeType(), item.url(), item.name());
        return toMedia(distributedKeyPrefix, part);
    }
}
