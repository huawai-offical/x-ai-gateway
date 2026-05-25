package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class OpenAiResponsesToolRegistry {

    public static final String SUPPORTED = "SUPPORTED";
    public static final String NATIVE_REQUIRED = "NATIVE_REQUIRED";
    public static final String BLOCKED = "BLOCKED";

    private static final String FUNCTION = "function";
    private static final String ALLOWED_TOOLS = "allowed_tools";

    private static final List<ToolCompatibility> MATRIX = List.of(
            new ToolCompatibility(
                    FUNCTION,
                    "custom-code",
                    SUPPORTED,
                    "映射为 canonical function tool definitions，并由现有 provider tool calling 路径执行。",
                    "已支持"
            ),
            new ToolCompatibility(
                    "web_search_preview",
                    "hosted",
                    BLOCKED,
                    "OpenAI hosted web search 需要 OpenAI Direct native route 与成本/外部访问审计；当前 canonical execution 不执行该工具。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "web_search_preview_2025_03_11",
                    "hosted",
                    BLOCKED,
                    "OpenAI hosted web search snapshot 需要 OpenAI Direct native route 与成本/外部访问审计；当前 canonical execution 不执行该工具。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "file_search",
                    "hosted",
                    NATIVE_REQUIRED,
                    "OpenAI hosted file_search 需要 OpenAI Direct native route 与 hosted file_search_call 生命周期；当前 canonical execution 不用本地 RAG 伪装该 hosted tool 成功。",
                    "TASK-20260524-001-04"
            ),
            new ToolCompatibility(
                    "computer_use_preview",
                    "hosted",
                    BLOCKED,
                    "Computer use 涉及远程操作、审批和安全审计；当前 canonical execution 不执行该工具。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "code_interpreter",
                    "hosted",
                    BLOCKED,
                    "Code interpreter 依赖 Containers 与 code interpreter 文件生命周期；当前 canonical execution 不执行该工具。",
                    "TASK-20260514-024"
            ),
            new ToolCompatibility(
                    "image_generation",
                    "hosted",
                    BLOCKED,
                    "Responses hosted image_generation 需要原生 Responses tool result passthrough；当前 canonical execution 不执行该工具。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "mcp",
                    "mcp",
                    BLOCKED,
                    "MCP tools 需要 server allowlist、审批、调用与结果回填；当前 canonical execution 不连接 MCP server。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "custom",
                    "custom",
                    BLOCKED,
                    "Responses custom tools 需要 custom_tool_call 输出生命周期；当前 canonical execution 只支持 function tools。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "apply_patch",
                    "side-effect",
                    BLOCKED,
                    "apply_patch 属于高 side effect 工具，必须有明确工作区、审批与审计边界；当前 gateway 不执行。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "shell",
                    "side-effect",
                    BLOCKED,
                    "shell 属于高 side effect 工具，必须有明确工作区、审批与审计边界；当前 gateway 不执行。",
                    "TASK-20260514-019"
            ),
            new ToolCompatibility(
                    "local_shell",
                    "side-effect",
                    BLOCKED,
                    "local shell 属于高 side effect 工具，必须有明确工作区、审批与审计边界；当前 gateway 不执行。",
                    "TASK-20260514-019"
            )
    );

    private static final Map<String, ToolCompatibility> BY_TYPE = MATRIX.stream()
            .collect(Collectors.toUnmodifiableMap(ToolCompatibility::type, Function.identity()));

    private OpenAiResponsesToolRegistry() {
    }

    public static List<ToolCompatibility> compatibilityMatrix() {
        return MATRIX;
    }

    public static void requireSupportedToolDefinition(JsonNode tool, int index) {
        String type = normalize(tool == null ? null : tool.path("type").asText(null), FUNCTION);
        ToolCompatibility compatibility = supportFor(type);
        if (!isAcceptedByMapper(compatibility)) {
            throw unsupported("Responses tool", type, compatibility, "tools[" + index + "]");
        }
    }

    public static void requireSupportedToolChoice(JsonNode toolChoice) {
        if (toolChoice == null || toolChoice.isMissingNode() || toolChoice.isNull() || toolChoice.isTextual()) {
            return;
        }
        if (!toolChoice.isObject()) {
            throw new IllegalArgumentException("Responses tool_choice 必须是 string 或 object。");
        }

        String type = normalize(toolChoice.path("type").asText(null), null);
        if (type == null) {
            return;
        }
        if (ALLOWED_TOOLS.equals(type)) {
            JsonNode tools = toolChoice.path("tools");
            if (tools.isArray()) {
                int index = 0;
                for (JsonNode allowedTool : tools) {
                    requireSupportedToolDefinition(allowedTool, index);
                    index++;
                }
            }
            return;
        }
        ToolCompatibility compatibility = supportFor(type);
        if (!isAcceptedByMapper(compatibility)) {
            throw unsupported("Responses tool_choice", type, compatibility, "tool_choice");
        }
    }

    private static boolean isAcceptedByMapper(ToolCompatibility compatibility) {
        return SUPPORTED.equals(compatibility.supportStatus())
                || NATIVE_REQUIRED.equals(compatibility.supportStatus());
    }

    private static ToolCompatibility supportFor(String type) {
        String normalized = normalize(type, FUNCTION);
        ToolCompatibility compatibility = BY_TYPE.get(normalized);
        if (compatibility != null) {
            return compatibility;
        }
        return new ToolCompatibility(
                normalized,
                "unknown",
                BLOCKED,
                "未知 Responses tool type 不会被 gateway canonical execution 执行。",
                "TASK-20260514-019"
        );
    }

    private static IllegalArgumentException unsupported(
            String subject,
            String type,
            ToolCompatibility compatibility,
            String location) {
        return new IllegalArgumentException(subject + " type '" + type + "' 当前状态为 "
                + compatibility.supportStatus()
                + "，gateway canonical execution 只支持 function tools；为避免静默跳过，本请求已拒绝。位置："
                + location
                + "。边界：" + compatibility.executionBoundary()
                + " 后续任务：" + compatibility.followUpTask() + "。");
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ToolCompatibility(
            String type,
            String category,
            String supportStatus,
            String executionBoundary,
            String followUpTask
    ) {
    }
}
