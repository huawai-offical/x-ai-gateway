# TASK-20260501-007 客户端接入包：Codex/Claude/Gemini CLI 配置导出、Deep Link、MCP/Prompts/Skills

状态：Done  
优先级：Medium  
来源：Linear X-288  
来源 URL：https://linear.app/x-ai/issue/X-288/客户端接入包codexclaudegemini-cli-配置导出deep-linkmcppromptsskills  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联需求：[REQ-20260506-004](../../docs/requirements/REQ-20260506-004-tenth-priority-task-closure-design.md)

## 背景

`CC Switch` 的核心优势是围绕 Claude Code、Codex、Gemini CLI、OpenCode、OpenClaw 的供应商切换、Deep Link 导入、MCP/Prompts/Skills 管理和云同步。当前 `x-ai-gateway` 已有客户端配置导出基础，但缺少面向 CLI 生态的一键接入包和配置分发体验。

## 目标

让用户可以从 `x-ai-gateway` 直接生成并导入 Codex / Claude / Gemini CLI 所需配置，降低接入成本。

## 范围

- 扩展 client config export，覆盖 Codex、Claude Code、Gemini CLI、OpenCode/OpenClaw 常见格式。
- 设计 `xag://` 或 HTTPS Deep Link 导入格式。
- 生成 MCP server、Prompts、Skills 配置片段或同步建议。
- Portal 和 Admin 均提供复制、下载、二维码/Deep Link 等入口。

## 非目标

- 不实现完整桌面应用。
- 不直接修改用户本机配置文件。

## 验收标准

- 至少 3 类 CLI 配置可以一键导出并通过示例验证。
- Secret 仍遵循一次性导出/轮换/审计规则。
- 文档提供接入步骤和故障排查。

## 实现记录

已完成后端可验证接入包：

- 新增 `GET /admin/distributed-keys/{id}/onboarding-pack`。
- 输出 Codex、Claude Code、Gemini CLI、OpenCode、OpenClaw 与 curl smoke 配置片段。
- 输出 `xag://` Deep Link、HTTPS import link、MCP server config、Prompts、Skills 与 troubleshooting。
- 扩展 `GatewayClientFamily`，支持 `OPENCODE`、`OPENCLAW` 和 `open-code`、`open-claw` 别名。
- 普通接入包仍只返回 masked key 占位，完整 secret 继续通过一次性 token 消费。
- 新增文档：[client-onboarding-pack](../../docs/client-onboarding-pack.md)。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests"
```

覆盖多 CLI 接入包、Deep Link、MCP、troubleshooting、secret 不泄露和 OpenCode/OpenClaw 归一化。

## 遗留问题

Claude Code 与 Gemini CLI 对自定义 base URL 的真实导入能力仍需后续在真实客户端中 smoke；当前先提供本地可复制配置片段和故障排查入口。
