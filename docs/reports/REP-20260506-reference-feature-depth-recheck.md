# REP-20260506 参考项目功能深度再复核

状态：Done  
日期：2026-05-06  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)  
关联任务：[TASK-20260506-016 参考项目功能深度复核与任务再生成](../../tasks/done/TASK-20260506-016-reference-depth-recheck-task-generation.md)

## 参考项目

- `D:/WorkSpace/Project/ai/参考/new-api-main`
- `D:/WorkSpace/Project/ai/参考/sub2api-main`
- `D:/WorkSpace/Project/ai/参考/cc-switch-main`
- `D:/WorkSpace/Project/ai/参考/cockpit-tools-main`
- `D:/WorkSpace/Project/ai/参考/cli_proxy-master`

## 复核口径

本轮不再只判断“有没有代码入口”，而是按功能完善度和功能丰富度分三层：

- 功能面是否存在：接口、持久化、管理端或公开文档是否能看到。
- 闭环是否完整：配置、运行时、异常分支、审计、测试、smoke 和运营入口是否完整。
- 生态是否丰富：provider、协议、CLI/AI IDE、支付渠道、部署形态、文档/i18n 是否达到成熟项目的广度。

## 当前项目快照

`x-ai-gateway` 已经具备明显的服务端网关主线：

- 后端包覆盖 `admin`、`gateway`、`infra`、`portal`、`protocol`、`provider`，数据库 changelog 到 `0045-payment-production-closure`。
- 协议入口覆盖 OpenAI、Anthropic、Gemini、Ollama、public docs、media task、files、batches、images、audio、uploads、realtime 等。
- 管理端功能目录覆盖 accounts、credentials、keys、models、network、operations、ops、portal、provider-sites、request-logs、resources、settings、traces、upstream-cache、user-domain、workbench。
- Provider Catalog 已扩到 18 个 preset，包括 OpenAI、Azure OpenAI、OpenAI-compatible generic、DeepSeek、Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、OpenRouter、Cohere、Jina、Together、Fireworks、Mistral、Anthropic、Gemini。
- 已归档的 `TASK-20260506-009` 到 `015` 已补齐 provider catalog、支付订单闭环、Realtime/Media matrix、CLI 云端接入、request filter、client family 和 docs/OpenAPI 最小事实源。

因此，本轮结论不是“当前项目缺少大多数核心能力”，而是：**主干能力已铺开，但多个能力仍处于骨架闭环或本地可验证闭环，距离参考项目的生产成熟度和生态丰富度仍有下一层任务。**

## 参考项目增量观察

### new-api-main

`new-api-main` 的功能丰富度主要体现在 channel/relay 宽度、支付计费和公开产品化：

- `relay/channel/` 下包含大量 provider/channel：Ali、AWS、Baidu、Claude、Cloudflare、Codex、Cohere、Coze、DeepSeek、Dify、Gemini、Jina、MiniMax、Mistral、Moonshot、Ollama、OpenAI、OpenRouter、Perplexity、Replicate、SiliconFlow、Tencent、Vertex、Volcengine、xAI、Xinference、Xunfei、Zhipu 等。
- task 类 media channel 覆盖 Sora、Suno、Kling、Vidu、Hailuo、Jimeng、Doubao、Gemini、Vertex 等。
- README 明确列出 OpenAI Responses、Realtime、Claude Messages、Gemini、Rerank、Midjourney、Suno、Dify、Image、Audio、Video、Rerank、Realtime 等接口。
- 支付与计费覆盖在线充值、Stripe/易支付、缓存计费、模型按次计费、Key 额度查询、模型定价和多语言。

对 `x-ai-gateway` 的差距判断：catalog metadata 已追上第一步，但真实 adapter、真实 provider smoke、价格同步、media 专有 adapter、公开 SDK/OpenAPI 自动化仍不够。

### sub2api-main

`sub2api-main` 的完善度主要体现在订阅配额分发、支付和部署运营：

- 核心功能覆盖多账号管理、API Key 分发、精确计费、智能调度、用户级/账号级并发、请求和 Token 速率限制。
- 支付支持 EasyPay、支付宝官方、微信官方、Stripe，面向用户自助充值。
- 部署侧有 Docker Compose、一键部署、在线升级/回滚、数据管理进程 `datamanagementd`。
- 对 Antigravity、Claude Code、Gemini 等客户端入口有专门说明。

对 `x-ai-gateway` 的差距判断：订单、退款、争议和对账 API 已有，但定时对账、发票税务、复杂订阅计费、跨币种结算、Linux/systemd 一键部署和数据迁移工具仍不足。

### cc-switch-main

`cc-switch-main` 的丰富度集中在桌面客户端生态：

- 统一管理 Claude Code、Codex、Gemini CLI、OpenCode、OpenClaw。
- 支持 provider 预设、一键切换、系统托盘、本地代理热切换、格式转换、故障转移、MCP/Prompts/Skills 管理、Session Manager、Workspace 编辑器、Deep Link、云同步、自动更新和多语言。

对 `x-ai-gateway` 的差距判断：云端 onboarding pack 和 client family 已有，但本机 MCP/Skills/Session/Workspace 管理不属于服务端网关默认主线。应先做条件性评估，不直接进入实现。

### cockpit-tools-main

`cockpit-tools-main` 的丰富度集中在 AI IDE/CLI 账号运营：

- 支持 Antigravity、Codex、GitHub Copilot、Windsurf、Kiro、Cursor、Gemini CLI、CodeBuddy、Qoder、Trae、Zed 等平台。
- 能做账号导入、配额/订阅识别、批量管理、多实例、切号注入、唤醒任务、设备指纹、本地 WebSocket/插件联动和 18 种语言。

对 `x-ai-gateway` 的差距判断：不应复制本机 profile 接管、设备指纹或切号注入，但应把“官方账号导入、配额刷新、client instance 运营、插件/Deep Link 授权下发”转为云端代理能力。

### cli_proxy-master

`cli_proxy-master` 的完善度集中在 CLI 代理体验：

- 支持 Claude/Codex 双代理、配置热切换、请求过滤、模型路由、Web UI 监控、使用统计、Docker 部署和健康检查。
- 过滤、模型路由和 active config 都围绕 CLI 请求体验做得很直接。

对 `x-ai-gateway` 的差距判断：云端 request filter 已有 replace/remove/mask 的 canonical text 版本，但 JSON path、tool schema、file metadata、策略审计、UI 管理、trace 联动和实时命中面板仍未闭环。

## 功能完善度矩阵

| 功能面 | 参考丰富度 | x-ai-gateway 当前状态 | 完善度判断 | 后续任务 |
| --- | --- | --- | --- | --- |
| Provider Catalog | new-api provider/channel 很宽，cc-switch 预设多 | 18 个 preset，能力矩阵和 conformance fixture 已有 | 基本实现，真实 smoke 和价格同步不足 | `TASK-20260506-017` |
| 原生 Provider Adapter | new-api 多 channel 真实 relay | 真实 adapter 主要集中 OpenAI/Anthropic/Gemini/Ollama，部分 provider 走 compatible/degradation | 部分实现 | `TASK-20260506-017`、`TASK-20260506-019` |
| Rerank/Dify | new-api 有 Rerank、Dify 文档和 channel | Catalog 有 Cohere/Jina/Dify 和 conformance 声明 | 基本实现，生产执行与 smoke 不足 | `TASK-20260506-017` |
| Media 专有生态 | new-api 覆盖 Sora/Suno/Kling/Vidu/Hailuo/Jimeng 等 task channel | 有 Video/Music matrix 和上游 task metadata | 部分实现 | `TASK-20260506-019` |
| Realtime | new-api 支持 Realtime，x-ai 已有 WebSocket adapter metadata | 事件契约、错误归一、binary frame metadata 已有 | 基本实现，长连接池和真实拨号 smoke 不足 | `TASK-20260506-019` |
| 支付与账务 | sub2api/new-api 覆盖多渠道充值、订阅、对账 | checkout、webhook、退款、争议、主动对账 API 已有 | 基本实现，定时对账/发票/跨币种不足 | `TASK-20260506-018` |
| 用户订阅与套餐 | sub2api SaaS/简易模式切换清晰 | plan/subscription/promo/portal 已有 | 基本实现，复杂订阅计费和税务弱 | `TASK-20260506-018` |
| CLI 云端接入 | cli_proxy 本地体验细，cc-switch/cockpit 覆盖多 CLI/IDE | 云端接入矩阵、onboarding pack、client family 已有 | 基本实现，插件/一次性授权下发不足 | `TASK-20260506-022` |
| Request Filter | cli_proxy 有 UI 和过滤器机制 | 云端 replace/remove/mask 已有，作用于 canonical chat text | 部分实现 | `TASK-20260506-020` |
| AI IDE/CLI 账号配额 | cockpit 覆盖多平台账号、配额、批量管理 | 当前只有云端 account pool、quota 字段、client metadata 契约 | 部分实现 | `TASK-20260506-021` |
| Client Instance 运营 | cockpit 多实例生命周期完整 | client instance/workspace hint 仅作为请求 metadata | 部分实现 | `TASK-20260506-022` |
| OpenAPI/SDK/i18n | new-api 多语言和文档产品化较强 | 最小 OpenAPI、docs bundle、zh-CN/en-US policy 已有 | 部分实现 | `TASK-20260506-023` |
| 部署与数据管理 | sub2api 一键部署、在线升级、datamanagementd | Docker Compose、PowerShell install/upgrade/rollback 已有 | 部分实现 | `TASK-20260506-024` |
| 迁移兼容 | new-api/One API 生态迁移价值高 | 当前自有 Spring/Postgres/Liquibase 模型 | 未实现/条件需求 | `TASK-20260506-024` |
| 本机 MCP/Skills/Session/Workspace | cc-switch 桌面体验完整 | 当前仅 onboarding 文案或云端边界说明 | 未实现/不默认照搬 | `TASK-20260506-025` |

## 新增后续任务

本轮将缺失或不完善项拆成以下 backlog：

- [TASK-20260506-017 Provider 真实凭证 Smoke 与价格同步自动化](../../tasks/backlog/TASK-20260506-017-provider-smoke-pricing-sync.md)
- [TASK-20260506-018 支付定时对账、订阅发票与跨币种结算](../../tasks/backlog/TASK-20260506-018-payment-scheduled-reconcile-invoice-currency.md)
- [TASK-20260506-019 Realtime 长连接池与专有 Media Adapter](../../tasks/backlog/TASK-20260506-019-realtime-pool-media-adapters.md)
- [TASK-20260506-020 云端 Request Filter 高级规则、审计与 UI](../../tasks/backlog/TASK-20260506-020-cloud-request-filter-audit-ui.md)
- [TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新](../../tasks/backlog/TASK-20260506-021-ai-ide-account-import-quota-refresh.md)
- [TASK-20260506-022 Client Instance 管理与插件/Deep Link 授权下发](../../tasks/backlog/TASK-20260506-022-client-instance-plugin-deeplink.md)
- [TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](../../tasks/backlog/TASK-20260506-023-openapi-sdk-frontend-i18n.md)
- [TASK-20260506-024 Linux/systemd 部署、数据管理与迁移兼容](../../tasks/backlog/TASK-20260506-024-linux-systemd-data-migration.md)
- [TASK-20260506-025 桌面 Companion、MCP/Skills/Session/Workspace 可行性评估](../../tasks/backlog/TASK-20260506-025-desktop-companion-local-workflow-evaluation.md)

## 优先级建议

1. `TASK-20260506-017`：先把 provider catalog 从“声明丰富”推进到“真实 smoke 和价格同步可信”。
2. `TASK-20260506-020`：request filter 已在主链路里，继续补 UI/审计/JSON path 可以快速提高生产可控性。
3. `TASK-20260506-019`：Realtime/Media 是协议丰富度的硬差距，适合和 provider smoke 并行推进。
4. `TASK-20260506-018`：支付生产闭环已具备骨架，定时对账和发票/跨币种决定商业可用性。
5. `TASK-20260506-021` 与 `022`：面向 AI IDE/CLI 账号运营，是客户端生态第二阶段。
6. `TASK-20260506-023`：持续降低文档漂移和 i18n 欠账。
7. `TASK-20260506-024`：面向私有化部署和迁移用户，商业交付前应补。
8. `TASK-20260506-025`：仅做可行性评估，不默认实现桌面工具能力。

## 不建议直接照搬

- 本机 profile 接管、设备指纹写入、切号注入和自动扫描本地 workspace，不进入 `x-ai-gateway` 默认路线。
- 本地 proxy 部署不作为默认交付形态；现有路线仍是云端 gateway endpoint。
- Session/Workspace/MCP/Skills 管理如果要做，应作为独立客户端 companion 评估，而不是塞进服务端网关主线。

## 验收结论

本轮已完成五个参考项目再复核，并把缺失或不完善项转为 9 个 backlog task。所有新增 task 均与本报告建立链接，后续可以按优先级逐项推进。
