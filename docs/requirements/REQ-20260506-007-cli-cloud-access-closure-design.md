# REQ-20260506-007 CLI 云端代理接入闭环设计

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-012 CLI/客户端生态与云端接入工具链补齐](../../tasks/done/TASK-20260506-012-cloud-cli-client-access-tooling.md)
- [TASK-20260506-014 CLI 云端代理接入、热切换、过滤与模型路由](../../tasks/done/TASK-20260506-014-cloud-cli-proxy-access-hot-switch-filtering.md)
- [TASK-20260506-015 AI IDE/CLI 云端账号配额、多实例与插件联动运营面](../../tasks/done/TASK-20260506-015-ai-ide-account-quota-instance-operator-plane.md)

## 背景

当前 `x-ai-gateway` 已有 OpenAI-compatible、Anthropic-compatible、Gemini-compatible、Responses、Realtime 等云端代理入口，并已有 distributed key、provider site、account pool、route policy、request logs、usage rollup、onboarding pack 等基础能力。

本轮用户明确校准：`x-ai-gateway` 是云端代理，任何 CLI/AI IDE 都应直接接入云端 endpoint，不应要求用户部署本地 proxy 或本地 agent。参考项目中的本地 proxy、桌面账号管理和插件联动体验可以借鉴，但实现形态必须落到云端接入矩阵、云端策略、云端过滤、云端模型映射、云端账号池和审计。

## 目标

- 明确任意 CLI/AI IDE 接入云端 `x-ai-gateway` 的协议矩阵和配置方式。
- 建立 CLI 请求的云端识别模型：client family、client instance、workspace hint、access group、distributed key。
- 建立云端 request filter 和 model/config/site mapping 的最小闭环。
- 让 route policy 热切换对后续 CLI 请求生效，并留下可观测和审计证据。
- 提供 Claude Code、Codex、Gemini CLI、OpenCode、OpenClaw 的接入样例和 smoke 命令。

## 范围

- 后端公开 docs bundle 与 onboarding pack 的 CLI 矩阵、示例配置、Deep Link/一次性 secret 描述。
- 路由/策略执行前的 CLI metadata 解析与传递。
- 云端 request filter 的规则模型、匹配范围、脱敏策略和测试。
- model mapping / site mapping 与 Provider Catalog、Route Policy 的事实源关系。
- 管理端或公开接口可观察 CLI 请求维度。
- 本地任务、ADR、测试证据和交付回写。

## 非目标

- 不在用户机器部署本地 proxy、desktop app、agent 或系统托盘工具。
- 不读取、扫描或上传用户本地 workspace、CLI session、IDE 配置文件。
- 不实现设备指纹绕过或任何规避平台风控的能力。
- 不提交真实 provider key、用户账号 secret 或真实 OAuth token。

## 风险

- 云端 request filter 会处理真实请求体，必须有权限隔离、审计、脱敏和回滚。
- model mapping、route policy、provider catalog 若形成多个事实源，会导致路由结果不可解释。
- 不同 CLI 的兼容协议会变化，需要以 smoke fixture 与 docs 矩阵持续校准。
- 已经发出的长连接/流式请求无法被“热切换”改变，只能保证后续请求命中新策略。

## 验收标准

- ADR 明确“云端代理、任意 CLI 接入、无本地 proxy”的架构边界。
- 012/014/015 三个 CLI 相关任务均移动到 `tasks/done/` 并补充实现结果、验证情况、遗留问题。
- 公开 docs bundle 或 onboarding pack 能输出至少 Claude Code、Codex、Gemini CLI 的云端接入样例。
- 云端 request filter、model mapping 或 CLI metadata 至少有一个可运行实现闭环，并有自动化测试。
- `tasks/index.md`、`docs/index.md`、相关报告均更新到最终状态。

## 实现结果

- 新增云端 CLI 接入矩阵：`CloudCliClientMatrixService` 覆盖 Codex、Claude Code、Gemini CLI、OpenCode、OpenClaw、Cursor、Windsurf、Kiro、GitHub Copilot-compatible。
- 扩展 `GatewayClientFamily` 与 `GatewayClientFamilyResolver`，支持 header/`User-Agent` 识别 CLI/AI IDE，并把 client family 传入 OpenAI Chat、Responses、Anthropic Messages、Gemini GenerateContent 的云端路由链路。
- 新增 `gateway.cli.request-filter` canonical request filter，支持 `replace`、`remove`、`mask`，按 `clientFamilies` 与 `role` 作用，非法规则降级为 skipped。
- `GatewayChatExecutionService` 在 route selection 前应用云端 filter，并把 `x_ai_gateway_filter.applied_rule_ids/skipped_rule_ids` 写入内部 route body 供 trace 排查。
- `DistributedKeyAdminService` onboarding pack 增加 Cursor、Windsurf、Kiro、GitHub Copilot-compatible 配置片段，并输出 `X_AI_GATEWAY_CLIENT_FAMILY`、`X_AI_GATEWAY_CLIENT_INSTANCE`、`X_AI_GATEWAY_WORKSPACE_HINT`。
- `PublicDocsBundleService` 增加公开 CLI 接入矩阵，明确所有 CLI 直接连云端 endpoint，不需要本地 proxy/agent。
- `docs/client-onboarding-pack.md` 回写云端 CLI metadata、request filter、安全边界和测试覆盖。

## 验证结果

- 通过：
  `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamilyResolverTests" --tests "com.prodigalgal.xaigateway.gateway.core.cli.CloudCliRequestFilterServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.DistributedKeyAdminServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentControllerTests"`。
- 验证覆盖：CLI family alias/User-Agent 识别、request filter replace/remove/mask、非法规则降级、client family 进入路由请求、公开 docs CLI matrix、onboarding pack 多 CLI 配置、四类 chat ingress controller 回归。

## 遗留问题与后续建议

- 更细的账号配额、订阅状态、client instance 运营面仍建议作为后续管理端 UI/报表任务推进；本轮已完成后端标识、接入契约和安全边界。
- model mapping 与 provider catalog 的更完整 conformance 已由 [TASK-20260506-009](../../tasks/done/TASK-20260506-009-provider-ecosystem-conformance.md) 承接并完成当前阶段闭环。
