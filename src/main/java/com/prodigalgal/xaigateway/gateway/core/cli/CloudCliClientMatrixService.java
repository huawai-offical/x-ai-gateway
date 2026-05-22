package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CloudCliClientMatrixService {

    private static final List<String> METADATA_HEADERS = List.of(
            "X-AI-Gateway-Client-Family",
            "X-AI-Gateway-Client-Instance",
            "X-AI-Gateway-Workspace-Hint"
    );

    public List<CloudCliClientDescriptor> clients() {
        return List.of(
                client("Codex", GatewayClientFamily.CODEX, "openai", "/v1", "Authorization: Bearer <Distributed Key>"),
                client("Claude Code", GatewayClientFamily.CLAUDE_CODE, "anthropic-compatible", "/v1/messages", "x-api-key: <Distributed Key>"),
                client("Gemini CLI", GatewayClientFamily.GEMINI_CLI, "gemini-compatible", "/v1beta/models", "x-goog-api-key 或 key=<Distributed Key>"),
                client("OpenCode", GatewayClientFamily.OPENCODE, "openai", "/v1", "Authorization: Bearer <Distributed Key>"),
                client("OpenClaw", GatewayClientFamily.OPENCLAW, "openai", "/v1", "Authorization: Bearer <Distributed Key>"),
                client("Cursor", GatewayClientFamily.CURSOR, "openai", "/v1", "Authorization: Bearer <Distributed Key>"),
                client("Windsurf", GatewayClientFamily.WINDSURF, "openai", "/v1", "Authorization: Bearer <Distributed Key>"),
                client("Kiro", GatewayClientFamily.KIRO, "openai", "/v1", "Authorization: Bearer <Distributed Key>"),
                client("GitHub Copilot-compatible", GatewayClientFamily.GITHUB_COPILOT, "openai", "/v1", "Authorization: Bearer <Distributed Key>")
        );
    }

    private CloudCliClientDescriptor client(
            String client,
            GatewayClientFamily family,
            String protocol,
            String basePath,
            String auth) {
        return new CloudCliClientDescriptor(
                client,
                family,
                protocol,
                basePath,
                List.of(auth),
                METADATA_HEADERS,
                List.of(
                        "直接连接云端 x-ai-gateway endpoint。",
                        "不需要在用户机器上部署本地 proxy 或 agent。",
                        "route policy、账号分组、request filter 与模型映射均在云端生效。"
                )
        );
    }
}
