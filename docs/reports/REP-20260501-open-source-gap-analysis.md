# REP-20260501 对标 new-api / Sub2API / CC Switch 的差距分析与增强任务

状态：Migrated  
日期：2026-05-01  
来源 Notion：https://app.notion.com/p/35379afa4790819ba20bccc6e092eb0e  
关联任务索引：[tasks/index.md](../../tasks/index.md)

## 背景

本报告记录 2026-05-01 对 `x-ai-gateway` 与三个本地参考项目的横向分析结果：

- `D:/WorkSpace/Project/ai/参考/new-api-main`
- `D:/WorkSpace/Project/ai/参考/sub2api-main`
- `D:/WorkSpace/Project/ai/参考/cc-switch-main`

目标不是照搬参考项目，而是识别 `x-ai-gateway` 在功能完成度、功能完善度、功能丰富度、具体实现和产品细节上的差距，并将可执行增强项沉淀到本地任务。

## 参考项目定位

| 项目 | 主要定位 | 可借鉴重点 |
| --- | --- | --- |
| new-api | 下一代 LLM Gateway 与 AI 资产管理系统 | 多供应商/多协议覆盖、计费与充值、模型价格、权限分组、多语言、丰富部署与 API 文档 |
| Sub2API | 面向 AI 订阅配额分发的 API 网关平台 | 多账号与订阅配额、精确计费、并发/速率限制、内置支付、渠道监控、设置向导、在线升级 |
| CC Switch | 面向 Claude Code / Codex / Gemini CLI / OpenCode / OpenClaw 的桌面管理工具 | CLI 配置接入、供应商预设、Deep Link、MCP/Prompts/Skills 管理、代理热切换、会话与用量追踪 |

## 当前 x-ai-gateway 基线

`x-ai-gateway` 当前已经具备较完整的网关内核和控制面：

- 后端：Spring Boot / Spring AI，模块包括 `admin`、`gateway`、`protocol`、`provider`、`portal`、`infra`。
- 协议入口：OpenAI Chat/Responses/Embeddings/Audio/Images/Files/Uploads/Batches/Fine-tuning/Realtime client secret，Anthropic Messages/Message Batches，Gemini Generate/Embeddings/Files/Batches，Ollama Native。
- Provider：一等 family 仍以 `OPENAI / ANTHROPIC / GEMINI / OLLAMA` 为核心，`UpstreamSiteKind` 扩展到了 Azure、DeepSeek、Grok、Mistral、Cohere、Together、Fireworks、OpenRouter、Vertex 等站点类型。
- 控制台：上游接入、访问控制、用户域、网络治理、运行工作台、站点真相、策略与操作、外部联动、Portal 等主域基本存在。
- 治理：DistributedKey、AccessGroup、账号池、路由、亲和、并发、预算、usage/audit、SLO/Capacity、ErrorRule、ChangePlan、Runbook、Webhook、外部应用、缓存资源、Lineage、Ops/Trace 等已经有明显工程深度。

## 总体判断

`x-ai-gateway` 的优势在于“网关治理内核”和“白盒调试/可观测”已经超过传统中转面板的粗粒度能力；但相对三个参考项目，产品化完成度仍有几类明显缺口：

- 供应商生态宽度不足：参考项目特别是 `new-api` 已覆盖大量厂商、聚合站、图片/视频/重排/任务型能力，`x-ai-gateway` 当前偏四大协议与 OpenAI-compatible 站点。
- SaaS 运营闭环不足：`Sub2API` 与 `new-api` 的支付、订单、充值、订阅、账单、模型价格、用量扣费更完整，`x-ai-gateway` 当前有用户域、套餐、兑换码和余额账本痕迹，但缺少在线支付与完整账单结算闭环。
- 客户端生态接入不足：`CC Switch` 在 Codex/Claude/Gemini CLI 配置、Deep Link、MCP/Prompts/Skills、会话和本地代理体验上非常强，`x-ai-gateway` 当前更多是服务端网关与 Web 控制台。
- 生产交付外圈不足：参考项目具备 Docker Compose、安装脚本、`.env.example`、systemd、在线升级/回滚、多语言文档等；当前仓库未看到等价的生产部署包与升级链路。
- 安全与账号体系仍需产品化：参考项目有 OIDC、2FA/Passkey/TOTP、验证码/Turnstile、注册策略、SSRF/敏感词等体系；`x-ai-gateway` 当前更偏 admin auth 与网关鉴权，公开运营所需安全面还不够完整。

## 差距维度

### 1. 功能完成度

- 已完成较好：核心协议入口、路由选择、账号池、分发 Key、能力矩阵、观测、缓存/资源谱系、Ops 控制面。
- 半完成或待产品化：Portal 用户自助、计费结算、部署升级、Realtime 真实 WebSocket、长期监控 rollup、公开 API 文档。
- 明显缺失：在线支付、订单/发票/退款、供应商预设市场、Deep Link 接入、桌面/CLI 助手、多语言国际化、Rerank/Video/Task 型资源族。

### 2. 功能完善度

- 路由与治理能力已有较多工程基础，但还需要权重随机、自动 retry/fallback、熔断窗口、账号/用户/模型多维限流策略的统一可视化和可验证策略编排。
- 用量记录存在，但需要更完整的用户账单、账户账单、渠道账单、成本/收入差异、长周期 rollup、导出和清理策略。
- 账号 OAuth 已覆盖 OpenAI/Gemini/Claude 基础形态，但缺少 Codex/Antigravity/Copilot 等编程产品身份与额度同步细节。

### 3. 功能丰富度

- `new-api` 的强项是大厂商/中转站/多模态/任务型接口数量。
- `Sub2API` 的强项是订阅配额分发、支付、渠道监控、用户运营后台。
- `CC Switch` 的强项是开发者本地工具生态、配置切换、代理、会话与 MCP/Skills。
- `x-ai-gateway` 当前更像“可治理的企业级网关内核”，要向可运营产品靠拢，需要补齐上述外圈。

### 4. 具体实现差距

- Provider 模型：当前 `ProviderFamily` 仍固定为四类，`UpstreamSiteKind` 虽有更多站点枚举，但缺少像 `new-api` 那样的厂商 adapter 目录、默认 Base URL、模型动作、费用策略、错误模式、特殊签名规则的统一注册机制。
- 资源类型：当前 `TranslationResourceType` 未包含 `rerank`、`video`、`music`、`task`、`web_search` 等类型。
- Realtime：当前主要看到 OpenAI Realtime client secret 与 Live Session 模拟/管理能力，距离真实 WebSocket/SSE 代理与事件映射还有差距。
- 支付：当前未看到 `PaymentOrder`、`PaymentProviderInstance`、充值回调、对账、退款、支付审计等核心对象。
- 部署：当前仓库缺少 Dockerfile、docker-compose、`.env.example`、systemd/install 脚本和正式升级包。
- 国际化：前后端未看到系统性的 i18n 资源文件和语言切换结构。

## 增强任务建议

| 本地任务 | 标题 | 来源 |
| --- | --- | --- |
| [TASK-20260501-001](../../tasks/backlog/TASK-20260501-001-provider-registry-2.md) | Provider Registry 2.0：插件化供应商目录、预设导入与厂商元数据 | X-282 |
| [TASK-20260501-002](../../tasks/backlog/TASK-20260501-002-non-chat-resources.md) | 非 Chat 资源族扩展：Rerank / Video / Music / Task async lifecycle | X-283 |
| [TASK-20260501-003](../../tasks/backlog/TASK-20260501-003-realtime-streaming-proxy.md) | Realtime 与 Streaming 真实代理闭环：WebSocket/SSE、事件映射和 conformance | X-284 |
| [TASK-20260501-004](../../tasks/backlog/TASK-20260501-004-billing-payment-loop.md) | SaaS 计费与支付闭环：价格、余额、订单、支付渠道、Webhook、对账 | X-285 |
| [TASK-20260501-005](../../tasks/backlog/TASK-20260501-005-portal-self-service.md) | Portal 用户自助增强：用量、账单、渠道状态、个人资料、订单与支付 | X-286 |
| [TASK-20260501-006](../../tasks/backlog/TASK-20260501-006-programming-account-identity.md) | 编程类账号身份治理：Codex / Antigravity / Copilot / Claude Plan OAuth 与额度同步 | X-287 |
| [TASK-20260501-007](../../tasks/backlog/TASK-20260501-007-client-onboarding-pack.md) | 客户端接入包：Codex/Claude/Gemini CLI 配置导出、Deep Link、MCP/Prompts/Skills | X-288 |
| [TASK-20260501-008](../../tasks/backlog/TASK-20260501-008-routing-policy-2.md) | 路由策略 2.0：权重、自动 retry、fallback、熔断、用户/模型/账号限流可视化 | X-289 |
| [TASK-20260501-009](../../tasks/backlog/TASK-20260501-009-security-system.md) | 安全体系增强：OIDC、2FA/Passkey/TOTP、验证码、SSRF、敏感词与注册策略 | Notion 待创建 |
| [TASK-20260501-010](../../tasks/backlog/TASK-20260501-010-production-deployment-upgrade.md) | 生产部署与升级体系：Docker Compose、`.env.example`、install 脚本、在线升级/回滚 | Notion 待创建 |
| [TASK-20260501-011](../../tasks/backlog/TASK-20260501-011-monitoring-billing-rollup.md) | 监控与账务 rollup：长周期用量聚合、清理、导出、渠道健康日报 | Notion 待创建 |
| [TASK-20260501-012](../../tasks/backlog/TASK-20260501-012-i18n-public-docs-compatibility.md) | 国际化、公开文档与兼容性样例：OpenAPI、SDK 示例、多语言 UI/Docs | Notion 待创建 |

## 风险

- 参考项目中部分能力面向公开 SaaS，`x-ai-gateway` 是否要完整公开运营需先确认产品定位。
- 支付、OAuth、Passkey、OIDC、验证码等会引入安全合规和密钥管理复杂度，不宜作为小改动推进。
- 大量供应商 adapter 容易造成维护成本上升，需要先设计 registry 和 conformance harness，再逐步接入。
- 视频、音乐、任务型 API 上游差异大，应以 async resource 抽象和能力矩阵先行。

## 验收标准

- 本地 `tasks/` 中已创建一个差距增强总览任务，并拆出可独立排期的增强子任务。
- 每个子任务包含背景、目标、范围、非目标、验收标准和本地报告链接。
- 后续进入任何实现前，必须在对应本地 task 和本报告中补充实施方案与范围变化。

