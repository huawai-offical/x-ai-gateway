package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
public class OllamaGatewayChatRuntime implements GatewayChatRuntime {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public OllamaGatewayChatRuntime(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(CatalogCandidateView candidate) {
        return candidate.providerType() == ProviderType.OLLAMA_DIRECT;
    }

    @Override
    public GatewayChatRuntimeResult execute(GatewayChatRuntimeContext context) {
        ensureSupportedRequest(context.request());
        ObjectNode body = buildBody(context.request(), context.selectionResult().resolvedModelKey(), false);
        JsonNode response = client(context).post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null) {
            throw new IllegalStateException("Ollama 响应为空。");
        }
        int promptTokens = response.path("prompt_eval_count").asInt(0);
        int completionTokens = response.path("eval_count").asInt(0);
        return new GatewayChatRuntimeResult(
                response.path("message").path("content").asText(response.path("response").asText("")),
                new GatewayUsage(promptTokens, promptTokens, completionTokens, 0, 0, 0, 0, 0, null, promptTokens + completionTokens, response),
                List.of()
        );
    }

    @Override
    public Flux<ChatExecutionStreamChunk> executeStream(GatewayChatRuntimeContext context) {
        ensureSupportedRequest(context.request());
        ObjectNode body = buildBody(context.request(), context.selectionResult().resolvedModelKey(), true);
        return client(context).post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::decodeChunk)
                .concatWithValues(new ChatExecutionStreamChunk(null, "stop", GatewayUsage.empty(), true));
    }

    private WebClient client(GatewayChatRuntimeContext context) {
        WebClient.Builder builder = webClientBuilder.clone()
                .baseUrl(context.credential().getBaseUrl().replaceAll("/+$", ""));
        if (context.apiKey() != null && !context.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.apiKey());
        }
        return builder.build();
    }

    private void ensureSupportedRequest(ChatExecutionRequest request) {
        boolean hasMedia = request.messages().stream().anyMatch(message -> message.media() != null && !message.media().isEmpty());
        if (hasMedia) {
            throw new IllegalArgumentException("当前 Ollama 运行时暂不支持多媒体消息。");
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            throw new IllegalArgumentException("当前 Ollama 运行时暂不支持 tools。");
        }
    }

    private ObjectNode buildBody(ChatExecutionRequest request, String model, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);
        if (request.temperature() != null || request.maxTokens() != null) {
            ObjectNode options = body.putObject("options");
            if (request.temperature() != null) {
                options.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                options.put("num_predict", request.maxTokens());
            }
        }
        ArrayNode messages = body.putArray("messages");
        for (ChatExecutionRequest.MessageInput message : request.messages()) {
            if (message.content() == null || message.content().isBlank()) {
                continue;
            }
            ObjectNode item = messages.addObject();
            item.put("role", normalizeRole(message.role()));
            item.put("content", message.content());
        }
        return body;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        return switch (role.trim().toLowerCase()) {
            case "assistant", "model" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }

    private Flux<ChatExecutionStreamChunk> decodeChunk(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Flux.empty();
        }
        String[] lines = rawBody.split("\\r?\\n");
        List<ChatExecutionStreamChunk> chunks = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                JsonNode payload = objectMapper.readTree(line);
                String content = payload.path("message").path("content").asText(payload.path("response").asText(""));
                if (payload.path("done").asBoolean(false)) {
                    int promptTokens = payload.path("prompt_eval_count").asInt(0);
                    int completionTokens = payload.path("eval_count").asInt(0);
                    chunks.add(new ChatExecutionStreamChunk(
                            content == null || content.isBlank() ? null : content,
                            payload.path("done_reason").asText("stop"),
                            new GatewayUsage(promptTokens, promptTokens, completionTokens, 0, 0, 0, 0, 0, null, promptTokens + completionTokens, payload),
                            false,
                            List.of()
                    ));
                    continue;
                }
                if (content != null && !content.isBlank()) {
                    chunks.add(new ChatExecutionStreamChunk(content, null, GatewayUsage.empty(), false, List.of()));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("无法解析 Ollama stream 响应。", exception);
            }
        }
        return Flux.fromIterable(chunks);
    }
}
