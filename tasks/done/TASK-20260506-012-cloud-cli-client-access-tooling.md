# TASK-20260506-012 CLI/客户端生态与云端接入工具链补齐

状态：Done  
优先级：High  
来源：

- [REP-20260506 三个参考项目功能完成度复核](../../docs/reports/REP-20260506-reference-feature-completeness-review.md)
- [REP-20260506 五个参考项目深度对标分析](../../docs/reports/REP-20260506-five-reference-deep-analysis.md)

## 背景

对照 `cc-switch-main`，`x-ai-gateway` 已能导出 Codex、Claude Code、Gemini CLI、OpenCode、OpenClaw 的 onboarding pack，但还需要把“任意 CLI 接入云端代理”的体验做成稳定产品能力：用户不应被要求部署本地 proxy，只需要配置 base URL、API key、兼容协议和必要的 client 参数，即可接入云端 `x-ai-gateway`。

本轮新增对照 `cockpit-tools-main` 与 `cli_proxy-master` 后，客户端生态差距被进一步拆清：这些项目的本地体验值得借鉴，但落到 `x-ai-gateway` 时应抽象成云端代理能力，包括云端 route policy 热切换、云端请求过滤、模型映射、账号池/站点权重、实时观察和 CLI 接入矩阵，而不是把 proxy 部署到用户本机。

## 目标

- 明确 `x-ai-gateway` 的 CLI/客户端接入策略：云端代理优先，不依赖本地 proxy。
- 在不泄露 secret 的前提下，让服务端生成的接入包能被任意 CLI/AI IDE 使用。
- 评估 MCP/Prompts/Skills/Session/Workspace 的云端可用范围和安全边界。
- 评估云端 CLI 接入能力是否作为客户端生态第一阶段。
- 评估 AI IDE 账号配额、多实例和插件联动哪些应由云端代理提供，哪些不进入范围。

## 范围

- Deep Link schema 与安全策略设计。
- CLI 配置片段导入/导出 contract。
- MCP/Prompts/Skills 的只读/写入边界评估。
- CLI/Workspace 会话元数据是否进入云端范围的决策记录。
- 云端 CLI 接入矩阵、兼容 endpoint、示例配置和 smoke 命令。
- Codex/GitHub Copilot/Gemini CLI 等高频入口的云端账号、配额与路由策略可行性。

## 非目标

- 不在本任务中直接复刻完整 CC Switch 桌面应用。
- 不在本任务中直接复刻完整 Cockpit Tools 桌面应用。
- 不要求用户部署本地 proxy 或本地 agent。
- 不自动读取、扫描或上传用户本机会话内容、workspace 文件、IDE 配置或账号 secret。

## 验收标准

- 产出 ADR，明确任意 CLI 接入云端代理的协议、范围和安全边界。
- 若推进，拆分独立实现任务和安全边界。
- onboarding pack 与公开 docs 对 Deep Link、MCP、Prompts、Skills 的描述一致。
- 子任务 [TASK-20260506-014](TASK-20260506-014-cloud-cli-proxy-access-hot-switch-filtering.md) 与 [TASK-20260506-015](TASK-20260506-015-ai-ide-account-quota-instance-operator-plane.md) 的范围、优先级和验收边界明确。

## 实现结果

- 已产出 [ADR-0008 云端代理优先的 CLI 接入架构](../../docs/decisions/ADR-0008-cloud-cli-access-without-local-proxy.md)，明确不要求用户部署本地 proxy、agent 或 desktop companion。
- 新增 `CloudCliClientMatrixService` 与公开 docs `cliClients` 响应，形成 Codex、Claude Code、Gemini CLI、OpenCode、OpenClaw、Cursor、Windsurf、Kiro、GitHub Copilot-compatible 的云端接入矩阵。
- `DistributedKeyAdminService.exportOnboardingPack` 已扩展多 CLI onboarding pack，配置片段默认只包含 masked key 占位，并增加 client family、client instance、workspace hint。
- `docs/client-onboarding-pack.md` 已同步云端 endpoint、metadata、安全边界、Deep Link 与 request filter 描述。

## 验证情况

- 通过关键测试：
  - `PublicDocsBundleServiceTests`
  - `DistributedKeyAdminServiceTests`
  - `GatewayClientFamilyResolverTests`
- 同轮命令：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolverTests" --tests "com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentControllerTests"`。

## 遗留问题

- 后续如要做 CLI/AI IDE 一键导入插件，需要独立设计插件授权与一次性 secret 消费流程；本任务已完成服务端接入契约和配置输出。

## 后续建议

- 将接入矩阵纳入 OpenAPI/公开 docs 自动发布流程，避免文档与真实 ingress drift。
