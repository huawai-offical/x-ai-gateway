package com.prodigalgal.xaigateway.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatParameterEvidenceTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path MATRIX_PATH = Path.of("src/test/resources/conformance/openai-chat-completions-parameter-parity.json");
    private static final Set<String> REQUIRED_PARAMETERS = Set.of(
            "store",
            "metadata",
            "response_format",
            "modalities",
            "audio",
            "web_search_options",
            "tools",
            "tool_choice",
            "functions",
            "function_call",
            "stream_options",
            "prediction",
            "service_tier",
            "parallel_tool_calls",
            "prompt_cache_key",
            "safety_identifier"
    );

    @Test
    void shouldKeepOpenAiChatParameterParityMatrixCompleteAndResolvable() throws IOException {
        JsonNode matrix = OBJECT_MAPPER.readTree(Files.readString(MATRIX_PATH));
        assertTrue(matrix.isArray(), "Chat 参数 parity matrix 必须是 JSON array。");
        assertTrue(matrix.size() >= 25, "Chat 参数 parity matrix 覆盖面过窄。");

        Map<String, JsonNode> byParameter = new LinkedHashMap<>();
        for (JsonNode item : matrix) {
            String parameter = item.path("parameter").asText();
            assertFalse(parameter.isBlank(), "parameter 不能为空。");
            assertTrue(byParameter.putIfAbsent(parameter, item) == null, "重复 parameter: " + parameter);
            assertFalse(item.path("status").asText().isBlank(), parameter + " status 不能为空。");
            assertFalse(item.path("mapping").asText().isBlank(), parameter + " mapping 不能为空。");
            assertTrue(item.path("evidenceSources").isArray(), parameter + " evidenceSources 必须是数组。");
            assertTrue(item.path("evidenceSources").size() > 0, parameter + " evidenceSources 不能为空。");
            for (JsonNode evidence : item.path("evidenceSources")) {
                Path evidencePath = Path.of(evidence.asText());
                assertTrue(Files.exists(evidencePath), parameter + " evidenceSource 不存在: " + evidencePath);
            }
        }

        for (String parameter : REQUIRED_PARAMETERS) {
            assertTrue(byParameter.containsKey(parameter), "缺少关键 Chat 参数证明: " + parameter);
        }
    }

    @Test
    void shouldKeepDocsOpenApiAndAdvancedSdkExampleAlignedToChatParameters() throws IOException {
        String compatibilityDocs = Files.readString(Path.of("docs/public-api-compatibility.md"));
        String sdkDocs = Files.readString(Path.of("docs/public-sdk-examples.md"));
        String advancedExample = Files.readString(Path.of("docs/sdk-examples/javascript/chat-advanced-parameters.mjs"));
        JsonNode openApi = OBJECT_MAPPER.readTree(Files.readString(Path.of("docs/openapi/public-openapi.json")));
        JsonNode chatProperties = openApi.path("paths")
                .path("/v1/chat/completions")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties");

        List<String> publicParameters = List.of(
                "response_format",
                "tools",
                "tool_choice",
                "store",
                "metadata",
                "web_search_options",
                "modalities",
                "audio"
        );
        for (String parameter : publicParameters) {
            assertTrue(compatibilityDocs.contains(parameter), "兼容文档缺少参数: " + parameter);
            assertTrue(sdkDocs.contains(parameter), "SDK 文档缺少参数: " + parameter);
            assertTrue(chatProperties.has(parameter), "OpenAPI schema 缺少参数: " + parameter);
        }

        List<String> exampleParameters = List.of(
                "response_format",
                "tools",
                "tool_choice",
                "store",
                "metadata",
                "web_search_options",
                "parallel_tool_calls",
                "service_tier",
                "stream_options"
        );
        for (String parameter : exampleParameters) {
            assertTrue(advancedExample.contains(parameter), "advanced JS 示例缺少参数: " + parameter);
        }
    }
}
