package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public class OpenAiResponsesLocalLifecycleService {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OpenAiResponsesLocalLifecycleService(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public JsonNode inputTokens(JsonNode requestBody) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "response.input_tokens");
        response.put("input_tokens", estimateInputTokens(requestBody));
        return response;
    }

    public JsonNode compact(JsonNode requestBody) {
        int inputTokens = estimateInputTokens(requestBody);
        String fingerprint = fingerprint(requestBody);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", "resp_compact_" + fingerprint.substring(0, 24));
        response.put("object", "response.compaction");
        response.put("created_at", clock.instant().getEpochSecond());
        ArrayNode output = response.putArray("output");
        appendReusableInput(requestBody == null ? null : requestBody.path("input"), output);
        ObjectNode compaction = output.addObject();
        compaction.put("type", "compaction");
        compaction.put("id", "cmp_" + fingerprint.substring(0, 24));
        compaction.put("encrypted_content", fingerprint);
        ObjectNode usage = response.putObject("usage");
        usage.put("input_tokens", inputTokens);
        usage.put("output_tokens", 0);
        usage.put("total_tokens", inputTokens);
        usage.putObject("input_tokens_details").put("cached_tokens", 0);
        usage.putObject("output_tokens_details").put("reasoning_tokens", 0);
        return response;
    }

    private int estimateInputTokens(JsonNode requestBody) {
        if (requestBody == null || requestBody.isMissingNode() || requestBody.isNull()) {
            return 0;
        }
        List<String> segments = new ArrayList<>();
        collectText(requestBody.path("instructions"), segments);
        collectText(requestBody.path("input"), segments);
        int codePoints = segments.stream().mapToInt(value -> value.codePointCount(0, value.length())).sum();
        if (codePoints == 0) {
            return 0;
        }
        int structuralTokens = Math.max(segments.size() * 2, countInputItems(requestBody.path("input")) * 4);
        return Math.max(1, (int) Math.ceil(codePoints / 4.0) + structuralTokens);
    }

    private void collectText(JsonNode node, List<String> segments) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            if (!node.asText().isBlank()) {
                segments.add(node.asText());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectText(item, segments);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> {
            if (shouldCountField(entry.getKey())) {
                collectText(entry.getValue(), segments);
            }
        });
    }

    private boolean shouldCountField(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return !List.of(
                "id",
                "type",
                "status",
                "role",
                "model",
                "name",
                "call_id",
                "file_id",
                "image_url",
                "file_url",
                "filename",
                "detail",
                "metadata",
                "stream",
                "stream_options",
                "store",
                "background"
        ).contains(normalized);
    }

    private int countInputItems(JsonNode input) {
        if (input == null || input.isMissingNode() || input.isNull()) {
            return 0;
        }
        if (input.isArray()) {
            return input.size();
        }
        return 1;
    }

    private void appendReusableInput(JsonNode input, ArrayNode output) {
        if (input == null || input.isMissingNode() || input.isNull()) {
            return;
        }
        if (input.isTextual()) {
            output.add(messageFromText(input.asText()));
            return;
        }
        if (input.isArray()) {
            for (JsonNode item : input) {
                if (item.isTextual()) {
                    output.add(messageFromText(item.asText()));
                } else {
                    output.add(item.deepCopy());
                }
            }
            return;
        }
        output.add(input.deepCopy());
    }

    private ObjectNode messageFromText(String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "message");
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "input_text");
        item.put("text", text == null ? "" : text);
        return message;
    }

    private String fingerprint(JsonNode requestBody) {
        try {
            String payload = objectMapper.writeValueAsString(requestBody == null ? objectMapper.createObjectNode() : requestBody);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化 Responses 本地 lifecycle 请求。", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 Responses 本地 lifecycle 指纹。", exception);
        }
    }
}
