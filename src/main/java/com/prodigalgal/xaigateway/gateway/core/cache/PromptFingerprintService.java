package com.prodigalgal.xaigateway.gateway.core.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class PromptFingerprintService {

    private static final Set<String> EXCLUDED_KEYS = Set.of(
            "stream",
            "stream_options",
            "user",
            "metadata",
            "request_id",
            "requestId",
            "client_request_id",
            "clientRequestId",
            "upstream_cache",
            "upstreamCache"
    );

    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    public PromptFingerprintService(ObjectMapper objectMapper, GatewayProperties gatewayProperties) {
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
    }

    public String buildPrefixHash(String protocol, String path, Object requestBody) {
        String canonical = canonicalize(protocol, path, requestBody);
        return sha256Hex(truncate(canonical, gatewayProperties.getCache().getFingerprintMaxPrefixTokens()));
    }

    public String buildFingerprint(String protocol, String path, Object requestBody) {
        String canonical = canonicalize(protocol, path, requestBody);
        return sha256Hex(canonical);
    }

    public String canonicalize(String protocol, String path, Object requestBody) {
        JsonNode source = objectMapper.valueToTree(requestBody);
        JsonNode normalized = extractStablePromptShape(normalizeProtocol(protocol), path, source);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化稳定请求形状。", exception);
        }
    }

    private String normalizeProtocol(String protocol) {
        return protocol == null ? "openai" : protocol.trim().toLowerCase(Locale.ROOT);
    }

    private JsonNode extractStablePromptShape(String protocol, String path, JsonNode requestBody) {
        return switch (protocol) {
            case "openai", "responses" -> normalizeJson(object(Map.of(
                    "messages", requestBody.path("messages"),
                    "tools", requestBody.path("tools"),
                    "response_format", requestBody.path("response_format"),
                    "reasoning_effort", requestBody.path("reasoning_effort"),
                    "thinking_config", requestBody.path("thinking_config"),
                    "google_search", requestBody.path("google_search")
            )));
            case "anthropic_native" -> normalizeJson(object(Map.of(
                    "system", requestBody.path("system"),
                    "messages", requestBody.path("messages"),
                    "tools", requestBody.path("tools"),
                    "tool_choice", requestBody.path("tool_choice"),
                    "thinking", requestBody.path("thinking"),
                    "betas", requestBody.path("betas")
            )));
            case "google_native" -> normalizeJson(object(Map.of(
                    "pathname", StringNode.valueOf(path == null ? "" : path.replace("/streamGenerateContent", "/generateContent")),
                    "contents", requestBody.path("contents"),
                    "systemInstruction", requestBody.path("systemInstruction"),
                    "tools", requestBody.path("tools"),
                    "toolConfig", requestBody.path("toolConfig"),
                    "generationConfig", requestBody.path("generationConfig")
            )));
            case "ollama_native" -> normalizeJson(object(Map.of(
                    "messages", requestBody.path("messages"),
                    "prompt", requestBody.path("prompt"),
                    "system", requestBody.path("system"),
                    "template", requestBody.path("template"),
                    "suffix", requestBody.path("suffix"),
                    "raw", requestBody.path("raw")
            )));
            default -> normalizeJson(requestBody);
        };
    }

    private ObjectNode object(Map<String, JsonNode> values) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        values.forEach(node::set);
        return node;
    }

    private JsonNode normalizeJson(JsonNode input) {
        if (input == null || input.isNull() || input.isMissingNode()) {
            return JsonNodeFactory.instance.nullNode();
        }

        if (input.isArray()) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : input) {
                arrayNode.add(normalizeJson(child));
            }
            return arrayNode;
        }

        if (input.isObject()) {
            ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
            Map<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = input.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (EXCLUDED_KEYS.contains(field.getKey()) || field.getValue().isMissingNode()) {
                    continue;
                }
                sorted.put(field.getKey(), normalizeJson(field.getValue()));
            }
            sorted.forEach(objectNode::set);
            return objectNode;
        }

        return input;
    }

    private String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256。", exception);
        }
    }
}
