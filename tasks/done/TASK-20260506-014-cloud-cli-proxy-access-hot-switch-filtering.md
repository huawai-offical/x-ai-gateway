# TASK-20260506-014 CLI 云端代理接入、热切换、过滤与模型路由

状态：Done  
优先级：High  
来源：[REP-20260506 五个参考项目深度对标分析](../../docs/reports/REP-20260506-five-reference-deep-analysis.md)

## 背景

对照 `cli_proxy-master`，它提供了 CLI 代理、active config 热切换、请求过滤、模型映射、号池权重和实时日志等优秀体验。但 `x-ai-gateway` 的产品定位是云端代理，不是在用户机器上部署本地 proxy；Claude Code、Codex、Gemini CLI、OpenCode、OpenClaw、Cursor、Windsurf、Kiro 等任意 CLI/AI IDE 都应通过 OpenAI-compatible、Anthropic-compatible、Gemini-compatible 或专属兼容入口接入云端 `x-ai-gateway`。

因此本任务不建设本地代理进程，而是把 `cli_proxy-master` 的体验抽象为云端 gateway 能力：云端配置热切换、云端请求过滤、云端模型映射、账号池/站点权重、失败回退、实时请求观察和面向任意 CLI 的接入文档。

## 目标

- 支持任意 CLI/AI IDE 通过云端 endpoint 接入 `x-ai-gateway`，不要求部署本地 proxy。
- 支持云端 active config/route policy 热切换，让后续 CLI 请求按最新策略路由。
- 支持云端 request filter，至少覆盖 replace/remove/mask 三类规则，并能按租户、key、access group 或 provider site 生效。
- 支持 model-mapping 和 config/site-mapping，并与 Provider Catalog、Route Policy、Account Pool 使用同一事实源。
- 提供云端请求日志、usage 统计、实时请求状态和可审计的策略变更记录。
- 输出 Claude Code、Codex、Gemini CLI、OpenCode、OpenClaw 等接入样例，说明只需改 base URL/API key/兼容协议即可接入。

## 范围

- 云端 CLI 接入矩阵：OpenAI-compatible、Anthropic-compatible、Gemini-compatible、Responses、Realtime 等入口如何对应不同 CLI。
- 云端配置 schema：route policy、provider site、account pool、request filter、model mapping 的关系。
- request filter 的安全边界、默认脱敏、审计和回滚策略。
- model-mapping/config-mapping 与 provider catalog/conformance 的一致性校验。
- 实时观察入口：请求开始、路由命中、过滤命中、上游选择、失败回退、usage 汇总。
- Windows/macOS/Linux CLI 接入文档与 smoke 命令样例，但不部署本地 agent。

## 非目标

- 不在用户机器上部署或托管本地 proxy 进程。
- 不读取、扫描或上传用户本地 workspace、CLI 会话、IDE 配置文件。
- 不替代用户已有 CLI，只提供云端兼容 endpoint 和接入配置。
- 不提交真实 provider key 或用户账号 secret。

## 风险

- 云端 request filter 会处理真实请求体，需要严格的权限、审计、脱敏、回滚和测试。
- model mapping 与 route policy 若拆成多个事实源，容易出现路由结果不可解释。
- 不同 CLI 的协议细节会变化，需要以 conformance fixture 和公开接入矩阵持续校准。
- 热切换只能保证后续请求命中新策略，不能改变已经发出的长连接或流式请求。

## 验收标准

- 有 ADR 或设计文档说明“云端代理、任意 CLI 接入、无本地部署”的架构边界。
- 至少覆盖 Claude Code、Codex、Gemini CLI 三类 CLI 的云端接入样例。
- request filter 有单元测试和 smoke，覆盖 replace/remove/mask、空规则、非法规则降级、权限隔离。
- model-mapping/config-mapping 有单元测试，覆盖命中、未命中、目标 provider site 不存在、与 catalog 不一致。
- route policy 热切换有验证，证明后续 CLI 请求使用最新云端策略。
- usage/log 记录默认不包含明文 secret，策略变更和过滤命中有审计记录。
- 本地文档、任务状态和验证结果回写完成。

## 实现结果

- 已按 [ADR-0008](../../docs/decisions/ADR-0008-cloud-cli-access-without-local-proxy.md) 保持“云端代理、任意 CLI 接入、无本地部署”边界。
- OpenAI Chat、OpenAI Responses、Anthropic Messages、Gemini GenerateContent 入口已接入 `X-AI-Gateway-Client-Family` 与 `User-Agent` 解析，并将 `GatewayClientFamily` 传入 `RouteSelectionRequest`。
- 新增 `CloudCliRequestFilterService`，支持 canonical chat text 的 `replace`、`remove`、`mask` 三类云端 request filter。
- 新增 `gateway.cli.request-filter.enabled/rules` 配置模型；非法 action、空 contains、client family 不匹配会进入 skipped，不阻断请求。
- `GatewayChatExecutionService` 在路由前应用 filter，过滤后的 canonical request 进入路由、执行和 lifecycle；过滤命中写入内部 route body 的 `x_ai_gateway_filter`。
- 命名空间别名 `/anthropic/v1/messages`、`/google/v1beta/models/*` 已同步透传 CLI metadata。
- model mapping/config/site mapping 继续复用既有 Provider Catalog、Model Alias、Route Policy、Account Pool 事实源；更广的 provider conformance 由 `TASK-20260506-009` 继续承接。

## 验证情况

- 新增并通过：
  - `CloudCliRequestFilterServiceTests`：覆盖 replace/remove/mask、非法规则降级、client family 权限隔离。
  - `GatewayChatExecutionServiceTests.shouldApplyCloudCliFilterAndRouteWithClientFamily`：验证 CLI family 进入 route selection，filter 命中写入内部 route body。
  - `GatewayClientFamilyResolverTests`：验证 OpenCode/OpenClaw/Cursor/Windsurf/Kiro/GitHub Copilot 等识别。
- 回归通过 OpenAI Chat、Responses、Anthropic、Gemini controller 测试，验证新参数未破坏既有请求。

## 遗留问题

- filter 当前作用于 canonical chat text；若后续要对 tool schema、providerExtensions、file metadata 做细粒度 JSON path 过滤，应拆独立增强任务。
- 管理端可视化的策略变更审计与 filter hit 面板尚未实现，本轮已提供后端 route body 证据点。

## 后续建议

- 将 filter rules 接入管理端策略 UI，并与 request trace 查询页联动展示 applied/skipped rule ids。
