# 客户端接入包

## 管理端入口

```text
GET /admin/distributed-keys/{id}/client-config
GET /admin/distributed-keys/{id}/onboarding-pack
POST /admin/distributed-keys/{id}/client-config/downloads/{grantToken}
```

`onboarding-pack` 会一次性返回 Codex、Claude Code、Gemini CLI、OpenCode、OpenClaw、Cursor、Windsurf、Kiro、GitHub Copilot-compatible 与 curl smoke 片段，并附带 Deep Link、MCP server config、Prompts、Skills 和故障排查建议。

所有 CLI/AI IDE 都直接连接云端 `x-ai-gateway` endpoint，不要求用户在本机部署 proxy、agent、desktop companion。

## Secret 策略

- 普通导出只返回 masked key 占位。
- 完整 secret 仅在创建或轮换后，通过一次性 `grantToken` 消费。
- Deep Link 不携带完整 secret，只携带 baseUrl、keyName、maskedKey 和导入元数据。

## 支持的客户端族

- `CODEX`
- `CLAUDE_CODE`
- `GEMINI_CLI`
- `OPENCODE`
- `OPENCLAW`
- `CURSOR`
- `WINDSURF`
- `KIRO`
- `GITHUB_COPILOT`
- `ANTHROPIC_COMPATIBLE`
- `GEMINI_COMPATIBLE`
- `GENERIC_OPENAI`

客户端族支持 `open-code`、`open-claw`、`copilot`、`anthropic`、`google-genai` 等常见别名归一化。服务端也会根据 `User-Agent` 识别 Codex、Claude、Gemini、OpenCode、OpenClaw、Cursor、Windsurf、Kiro、GitHub Copilot 等常见客户端。

## 云端 CLI metadata

推荐客户端带上以下可选 metadata，用于云端 route policy、账号池、request filter、usage/log 归因：

- `X-AI-Gateway-Client-Family`
- `X-AI-Gateway-Client-Instance`
- `X-AI-Gateway-Workspace-Hint`

这些 metadata 只描述客户端来源与实例，不读取、不扫描、不上传用户本地 workspace、会话记录、IDE profile 或账号 secret。

## 接入步骤

1. 创建或轮换 Distributed Key，保存一次性完整 secret。
2. 调用 `GET /admin/distributed-keys/{id}/onboarding-pack?baseUrl=https://gateway.example.com`。
3. 选择目标客户端配置片段，将完整 secret 写入 CLI 支持的 secret manager 或环境变量。
4. 使用返回的 curl smoke 验证 `/v1/chat/completions`。
5. 如请求失败，按返回的 troubleshooting 读取 trace、route policy runtime state、client family 约束与 request filter 命中情况。

## 云端 request filter

`gateway.cli.request-filter` 支持在云端对 canonical chat 请求执行 `replace`、`remove`、`mask` 三类规则。规则可按 `clientFamilies`、`role`、`contains` 生效；非法规则或不匹配的客户端族会被跳过，不阻断请求。

过滤命中会写入内部 route body 的 `x_ai_gateway_filter.applied_rule_ids` 与 `x_ai_gateway_filter.skipped_rule_ids`，供路由审计与 trace 排查使用。完整请求体仍遵循现有观测与脱敏策略，不记录明文 secret。

## MCP、Prompts 与 Skills

接入包中的 MCP config 使用 `@modelcontextprotocol/server-openapi` 作为 OpenAPI 入口示例。Prompts 和 Skills 只提供本地可复制文本，不会自动写入用户本机配置。

## 验收覆盖

- `DistributedKeyAdminServiceTests.shouldExportMultiCliOnboardingPackWithoutFullSecret`
- `DistributedKeyAdminServiceTests.shouldNormalizeOpenCodeAndOpenClawClientFamilies`
- `GatewayClientFamilyResolverTests`
- `CloudCliRequestFilterServiceTests`
- `GatewayChatExecutionServiceTests.shouldApplyCloudCliFilterAndRouteWithClientFamily`
