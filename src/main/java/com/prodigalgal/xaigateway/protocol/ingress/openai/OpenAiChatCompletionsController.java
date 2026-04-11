package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.auth.AuthenticatedDistributedKey;
import com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyAuthenticationService;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest.MessageInput;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionResponse;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamChunk;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionStreamResponse;
import com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/v1/chat/completions")
public class OpenAiChatCompletionsController {

    private final DistributedKeyAuthenticationService distributedKeyAuthenticationService;
    private final GatewayChatExecutionService gatewayChatExecutionService;
    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionsController(
            DistributedKeyAuthenticationService distributedKeyAuthenticationService,
            GatewayChatExecutionService gatewayChatExecutionService,
            ObjectMapper objectMapper) {
        this.distributedKeyAuthenticationService = distributedKeyAuthenticationService;
        this.gatewayChatExecutionService = gatewayChatExecutionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> createCompletion(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody OpenAiChatCompletionRequest request) {
        AuthenticatedDistributedKey distributedKey = distributedKeyAuthenticationService.authenticateBearerToken(authorization);

        List<MessageInput> messages = toMessages(request.messages());
        ensureUserMessage(messages);

        ChatExecutionRequest executionRequest = new ChatExecutionRequest(
                distributedKey.keyPrefix(),
                "openai",
                "/v1/chat/completions",
                request.model(),
                messages,
                toTools(request.tools()),
                request.toolChoice(),
                request.temperature(),
                request.maxTokens()
        );

        if (Boolean.TRUE.equals(request.stream())) {
            ChatExecutionStreamResponse streamResponse = gatewayChatExecutionService.executeStream(executionRequest);
            Flux<String> body = Flux.concat(
                    Flux.just(encode(OpenAiChatCompletionResponse.roleChunk(streamResponse.routeSelection().resolvedModelKey()))),
                    streamResponse.chunks().flatMap(chunk -> encodeChunk(streamResponse, chunk)),
                    Flux.just("data: [DONE]\n\n")
            );
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(body);
        }

        ChatExecutionResponse response = gatewayChatExecutionService.execute(executionRequest);

        return ResponseEntity.ok(OpenAiChatCompletionResponse.from(
                response.routeSelection().resolvedModelKey(),
                response.text(),
                response.usage(),
                response.toolCalls()
        ));
    }

    private List<MessageInput> toMessages(List<OpenAiChatCompletionRequest.Message> messages) {
        List<MessageInput> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (OpenAiChatCompletionRequest.Message message : messages) {
            ParsedMessageContent parsed = parseMessageContent(message.content());
            if ((parsed.text() == null || parsed.text().isBlank()) && parsed.media().isEmpty()) {
                continue;
            }
            String toolName = null;
            if ("tool".equalsIgnoreCase(message.role()) && message.toolCallId() != null) {
                toolName = "tool";
            }
            result.add(new MessageInput(message.role(), parsed.text(), message.toolCallId(), toolName, parsed.media()));
        }
        return List.copyOf(result);
    }

    private void ensureUserMessage(List<MessageInput> messages) {
        boolean hasUser = messages.stream()
                .anyMatch(message ->
                        "user".equalsIgnoreCase(message.role())
                                && ((message.content() != null && !message.content().isBlank())
                                || (message.media() != null && !message.media().isEmpty()))
                );
        if (!hasUser) {
            throw new IllegalArgumentException("至少需要一条 user 消息。");
        }
    }

    private List<GatewayToolDefinition> toTools(List<OpenAiChatCompletionRequest.Tool> tools) {
        List<GatewayToolDefinition> result = new ArrayList<>();
        if (tools == null) {
            return result;
        }
        for (OpenAiChatCompletionRequest.Tool tool : tools) {
            if (tool == null || tool.function() == null || tool.function().name() == null || tool.function().name().isBlank()) {
                continue;
            }
            result.add(new GatewayToolDefinition(
                    tool.function().name(),
                    tool.function().description(),
                    tool.function().parameters(),
                    tool.function().strict()
            ));
        }
        return List.copyOf(result);
    }

    private Flux<String> encodeChunk(ChatExecutionStreamResponse streamResponse, ChatExecutionStreamChunk chunk) {
        if (chunk.terminal()) {
            return Flux.just(encode(OpenAiChatCompletionResponse.finishChunk(
                    streamResponse.routeSelection().resolvedModelKey(),
                    chunk.finishReason() == null ? "stop" : chunk.finishReason()
            )));
        }

        if (chunk.textDelta() == null || chunk.textDelta().isBlank()) {
            return Flux.empty();
        }

        return Flux.just(encode(OpenAiChatCompletionResponse.contentChunk(
                streamResponse.routeSelection().resolvedModelKey(),
                chunk.textDelta()
        )));
    }

    private String encode(Object payload) {
        try {
            return "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 OpenAI stream 响应。", exception);
        }
    }

    private ParsedMessageContent parseMessageContent(com.fasterxml.jackson.databind.JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return new ParsedMessageContent("", List.of());
        }

        if (contentNode.isTextual()) {
            return new ParsedMessageContent(contentNode.asText(), List.of());
        }

        if (contentNode.isArray()) {
            String text = "";
            List<ChatExecutionRequest.MediaInput> media = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : contentNode) {
                String type = item.path("type").asText();
                if ("text".equalsIgnoreCase(type)) {
                    text = item.path("text").asText(text);
                }
                if ("image_url".equalsIgnoreCase(type)) {
                    String url = item.path("image_url").path("url").asText();
                    if (url != null && !url.isBlank()) {
                        media.add(new ChatExecutionRequest.MediaInput("image", "image/*", url, null));
                    }
                }
                if ("input_file".equalsIgnoreCase(type)) {
                    JsonNode inputFile = item.path("input_file");
                    String fileId = inputFile.path("file_id").asText(null);
                    if (fileId != null && !fileId.isBlank()) {
                        media.add(new ChatExecutionRequest.MediaInput(
                                "file",
                                inputFile.path("mime_type").asText("application/octet-stream"),
                                "gateway://" + fileId,
                                inputFile.path("filename").asText(fileId)
                        ));
                        continue;
                    }
                    String url = inputFile.path("url").asText(null);
                    if (url == null || url.isBlank()) {
                        url = inputFile.path("file_url").asText(null);
                    }
                    if (url == null || url.isBlank()) {
                        url = item.path("file_url").asText(null);
                    }
                    if (url != null && !url.isBlank()) {
                        media.add(new ChatExecutionRequest.MediaInput(
                                "file",
                                inputFile.path("mime_type").asText("application/octet-stream"),
                                url,
                                inputFile.path("filename").asText(null)
                        ));
                    }
                }
            }
            return new ParsedMessageContent(text, List.copyOf(media));
        }

        return new ParsedMessageContent(contentNode.toString(), List.of());
    }

    private record ParsedMessageContent(
            String text,
            List<ChatExecutionRequest.MediaInput> media
    ) {
    }
}
