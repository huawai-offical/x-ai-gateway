package com.prodigalgal.xaigateway.protocol.ingress.openai;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class OpenAiResponsesFileSearchBindingService {

    private static final int MAX_QUERY_CHARS = 2000;
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final GatewayAsyncResourceService gatewayAsyncResourceService;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesFileSearchBindingService(
            GatewayAsyncResourceService gatewayAsyncResourceService,
            ObjectMapper objectMapper) {
        this.gatewayAsyncResourceService = gatewayAsyncResourceService;
        this.objectMapper = objectMapper;
    }

    public JsonNode bindLocalVectorStores(Long distributedKeyId, JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            return requestBody;
        }
        JsonNode toolsNode = requestBody.path("tools");
        List<JsonNode> fileSearchTools = fileSearchTools(toolsNode);
        if (fileSearchTools.isEmpty()) {
            return requestBody;
        }
        rejectForcedFileSearchChoice(requestBody.path("tool_choice"));

        ObjectNode boundRequest = ((ObjectNode) requestBody).deepCopy();
        String query = extractQuery(boundRequest);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("file_search 本地基线无法从 input 或 instructions 提取查询文本。");
        }

        List<String> snippets = new ArrayList<>();
        for (JsonNode tool : fileSearchTools) {
            List<String> vectorStoreIds = vectorStoreIds(tool);
            for (String vectorStoreId : vectorStoreIds) {
                JsonNode page = gatewayAsyncResourceService.searchVectorStore(
                        vectorStoreId,
                        distributedKeyId,
                        searchRequest(query, tool)
                );
                collectSearchSnippets(vectorStoreId, page, snippets);
            }
        }

        injectContext(boundRequest, snippets);
        removeFileSearchTools(boundRequest);
        return boundRequest;
    }

    private List<JsonNode> fileSearchTools(JsonNode toolsNode) {
        if (toolsNode == null || !toolsNode.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode tool : toolsNode) {
            if ("file_search".equals(normalize(tool.path("type").asText(null)))) {
                result.add(tool);
            }
        }
        return result;
    }

    private void rejectForcedFileSearchChoice(JsonNode toolChoice) {
        if (toolChoice == null || toolChoice.isMissingNode() || toolChoice.isNull() || toolChoice.isTextual()) {
            return;
        }
        if (!toolChoice.isObject()) {
            throw new IllegalArgumentException("Responses tool_choice 必须是 string 或 object。");
        }
        String type = normalize(toolChoice.path("type").asText(null));
        if ("file_search".equals(type)) {
            throw new IllegalArgumentException("本地 file_search 基线不支持 tool_choice 强制 file_search；请使用 auto 或省略 tool_choice。");
        }
        if (!"allowed_tools".equals(type) || !toolChoice.path("tools").isArray()) {
            return;
        }
        for (JsonNode allowedTool : toolChoice.path("tools")) {
            if ("file_search".equals(normalize(allowedTool.path("type").asText(null)))) {
                throw new IllegalArgumentException("本地 file_search 基线不支持 tool_choice.allowed_tools 限定 file_search。");
            }
        }
    }

    private List<String> vectorStoreIds(JsonNode tool) {
        JsonNode ids = tool.path("vector_store_ids");
        if (!ids.isArray() || ids.isEmpty()) {
            throw new IllegalArgumentException("file_search.vector_store_ids 必须是非空数组。");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode id : ids) {
            if (!id.isTextual() || id.asText().isBlank()) {
                throw new IllegalArgumentException("file_search.vector_store_ids 只能包含非空 string。");
            }
            result.add(id.asText());
        }
        return List.copyOf(result);
    }

    private ObjectNode searchRequest(String query, JsonNode tool) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("query", query);
        if (tool.has("filters")) {
            request.set("filters", tool.get("filters").deepCopy());
        }
        if (tool.has("ranking_options")) {
            request.set("ranking_options", tool.get("ranking_options").deepCopy());
        }
        if (tool.has("max_num_results")) {
            request.set("max_num_results", tool.get("max_num_results").deepCopy());
        } else {
            request.put("max_num_results", DEFAULT_MAX_RESULTS);
        }
        return request;
    }

    private String extractQuery(JsonNode requestBody) {
        List<String> segments = new ArrayList<>();
        collectText(requestBody.path("instructions"), segments);
        collectText(requestBody.path("input"), segments);
        String joined = String.join("\n", segments).trim();
        if (joined.length() <= MAX_QUERY_CHARS) {
            return joined;
        }
        return joined.substring(0, MAX_QUERY_CHARS);
    }

    private void collectText(JsonNode node, List<String> segments) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (!text.isBlank()) {
                segments.add(text);
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
        if (node.path("text").isTextual()) {
            collectText(node.path("text"), segments);
        }
        if (!node.path("content").isMissingNode()) {
            collectText(node.path("content"), segments);
        }
        if (!node.path("input").isMissingNode()) {
            collectText(node.path("input"), segments);
        }
    }

    private void collectSearchSnippets(String vectorStoreId, JsonNode page, List<String> snippets) {
        JsonNode data = page == null ? null : page.path("data");
        if (data == null || !data.isArray()) {
            return;
        }
        for (JsonNode item : data) {
            String fileId = item.path("file_id").asText("");
            String filename = item.path("filename").asText(fileId);
            String text = item.path("content").path(0).path("text").asText("");
            if (text.isBlank()) {
                continue;
            }
            snippets.add("- vector_store_id=" + vectorStoreId
                    + ", file_id=" + fileId
                    + ", filename=" + filename
                    + ", score=" + item.path("score").asDouble(0.0d)
                    + "\n  " + text);
        }
    }

    private void injectContext(ObjectNode requestBody, List<String> snippets) {
        String existing = requestBody.path("instructions").isTextual()
                ? requestBody.path("instructions").asText()
                : "";
        StringBuilder injected = new StringBuilder();
        if (!existing.isBlank()) {
            injected.append(existing).append("\n\n");
        }
        injected.append("Local file_search context from gateway vector stores:\n");
        if (snippets.isEmpty()) {
            injected.append("- No local file_search results matched the current input.");
        } else {
            injected.append(String.join("\n", snippets));
        }
        injected.append("\n\nUse this local file_search context as retrieved reference material. Do not claim hosted OpenAI file_search execution.");
        requestBody.put("instructions", injected.toString());
    }

    private void removeFileSearchTools(ObjectNode requestBody) {
        JsonNode toolsNode = requestBody.path("tools");
        if (!toolsNode.isArray()) {
            return;
        }
        ArrayNode remaining = objectMapper.createArrayNode();
        for (JsonNode tool : toolsNode) {
            if (!"file_search".equals(normalize(tool.path("type").asText(null)))) {
                remaining.add(tool.deepCopy());
            }
        }
        if (remaining.isEmpty()) {
            requestBody.remove("tools");
        } else {
            requestBody.set("tools", remaining);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
