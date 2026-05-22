# TASK-20260506-015 AI IDE/CLI 云端账号配额、多实例与插件联动运营面

状态：Done  
优先级：High  
来源：[REP-20260506 五个参考项目深度对标分析](../../docs/reports/REP-20260506-five-reference-deep-analysis.md)

## 背景

对照 `cockpit-tools-main`，成熟的 AI IDE/CLI 工具已经不只是“导出配置”，而是能管理多个平台账号、读取配额与订阅、维护多实例、通过插件或 Deep Link 联动，并提供细致的账号运营体验。

`x-ai-gateway` 的定位是云端代理，不做本机桌面接管。本任务应把这些体验转化为云端代理可提供的能力：云端账号池、订阅/配额状态、CLI/AI IDE 接入配置、多 client instance 标识、插件/Deep Link 接入契约和审计，而不是读取或修改用户本机 IDE/CLI profile。

## 目标

- 设计 AI IDE/CLI 云端账号运营面的最小产品边界，优先评估 Codex、GitHub Copilot、Gemini CLI 三类入口。
- 明确云端账号导入、Token 刷新、配额查询、订阅识别、账号池调度和审计策略。
- 评估多 client instance 标识、client family、workspace hint、access group 与账号池绑定的云端模型。
- 设计云端插件/Deep Link 接入契约，支持未来从 IDE/CLI 一键获取云端接入配置。
- 明确哪些能力属于云端代理，哪些本机 profile/设备指纹/会话内容不进入范围。

## 范围

- Codex/GitHub Copilot/Gemini CLI 的云端接入方式、账号池与配额模型调研。
- 本地 secret 存储、加密、导入/导出、删除和清空策略。
- 配额查询模型：quota window、reset time、plan/subscription tier、quota error。
- 多实例模型：client instance id、client family、workspace hint、access group、bind account pool。
- Deep Link schema、插件接入 schema、云端配置下发和操作授权。
- 与 `TASK-20260506-014` 云端 CLI 代理接入的组合方案。

## 非目标

- 不一次性复刻 `cockpit-tools-main` 的所有平台矩阵。
- 不在没有明确授权时读取、上传或修改用户本地 IDE/CLI 配置。
- 不实现设备指纹绕过或平台风控规避能力。
- 不默认扫描 workspace 会话内容、聊天历史或本地源码。
- 不要求用户部署本地 companion、desktop app 或 proxy 进程。

## 风险

- 各 AI IDE/CLI 的云端兼容协议和 OAuth 行为变化快，需要 adapter 化和失败降级。
- 配额查询可能依赖非公开接口，需要明确稳定性、合法性和错误提示。
- 设备指纹和本机 profile 修改容易触碰平台风控，应明确排除。
- 插件或 Deep Link 若下发 Token，需要鉴权、最小权限、一次性 secret 和用户确认。

## 验收标准

- 产出 ADR，明确是否建设 AI IDE 账号运营面，以及首批平台范围。
- 给出 Codex/GitHub Copilot/Gemini CLI 的云端接入、账号池、配额和配置下发可行性调研结果。
- 定义账号、quota、client instance、plugin message 的云端数据模型草案。
- 定义不读取本机 profile、不扫描 workspace、不要求本地部署的安全准则。
- 若进入实现，拆分至少三个独立任务：账号导入/配额、client instance 管理、插件/Deep Link。
- 本地文档、任务状态和后续建议回写完成。

## 实现结果

- ADR-0008 已明确本轮不做本机 profile 接管、不读取本机 workspace、不要求本地部署；云端运营面以 distributed key、access group、account pool、client family、client instance、workspace hint 为边界。
- `GatewayClientFamily` 已扩展 Codex、Claude Code、Gemini CLI、OpenCode、OpenClaw、Cursor、Windsurf、Kiro、GitHub Copilot-compatible 等云端标识。
- onboarding pack 已输出 `X_AI_GATEWAY_CLIENT_INSTANCE` 与 `X_AI_GATEWAY_WORKSPACE_HINT`，为后续账号配额、usage/log、实例运营面提供稳定契约。
- Deep Link 文案已校准为只传递云端 endpoint 元数据和 masked key，不携带完整 secret，不要求本地 proxy。
- 已在 `docs/client-onboarding-pack.md` 定义 CLI metadata 安全边界：只描述客户端来源与实例，不读取、不扫描、不上传 workspace、session、IDE profile 或账号 secret。

## 验证情况

- 通过 `DistributedKeyAdminServiceTests.shouldExportMultiCliOnboardingPackWithoutFullSecret`，覆盖多 CLI 配置、masked-only secret、Deep Link 无本地 proxy 描述和 request filter troubleshooting。
- 通过 `GatewayClientFamilyResolverTests`，覆盖多 AI IDE/CLI family 归一化与 User-Agent 识别。
- 通过四类协议 controller 回归，确认新增 client family 参数不会破坏既有接入。

## 遗留问题

- 真实账号导入、订阅/配额查询、quota window/reset time 和账号池运营 UI 尚未建设；这些需要真实 provider/OAuth 能力与管理端页面，建议后续独立拆分。
- GitHub Copilot 的官方账号/配额接口稳定性仍需单独调研；本轮只提供云端兼容接入与标识契约。

## 后续建议

- 新增后续任务：账号导入/配额查询、client instance 管理页、插件/Deep Link 授权下发，分别独立排期，避免和本轮云端接入闭环混在一起。

## 2026-05-21 历史归档口径

- 本任务中 `account pool`、运营面和多实例 UI 的表述属于历史设计语境。
- 当前保留它是为了记录云端接入、client instance 与后端语义边界，不再把旧 `account pool` 控制台能力当作现役产品面。
