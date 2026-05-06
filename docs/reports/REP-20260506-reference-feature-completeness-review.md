# REP-20260506 三个参考项目功能完成度复核

状态：Done  
日期：2026-05-06  
参考项目：

- `D:/WorkSpace/Project/ai/参考/new-api-main`
- `D:/WorkSpace/Project/ai/参考/sub2api-main`
- `D:/WorkSpace/Project/ai/参考/cc-switch-main`

## 结论

当前 `x-ai-gateway` 已经不是早期“骨架项目”：多协议入口、分布式 Key、账号池、路由策略、Redis runtime store、账务 rollup、Portal、安全 MFA、社交 OAuth、生产部署脚本、Provider Catalog Marketplace、Realtime 与异步资源等主干能力都已经有代码、测试和本地文档。

但如果按这三个参考项目逐个功能要求“完全实现、完全完善”，结论是：**尚未完全实现，也尚未完全完善**。差距主要不是单点 API 缺失，而是成熟项目在真实生产使用里的生态宽度、支付闭环深度、客户端工具链、公开文档/i18n、以及真实 provider smoke 证据。

## 评估口径

- 完全实现：功能入口、持久化模型、运行时逻辑、管理/用户界面或公开 API 均存在，且有回归测试或可重放验证。
- 基本实现：主链路存在，但 provider 覆盖、UI 完整度、真实环境 smoke、异常分支或运营细节不足。
- 部分实现：有数据结构、接口或 mock/local contract，但未达到参考项目同等使用体验。
- 未实现：当前仓库未发现同类能力。
- 不建议照搬：参考项目能力与 `x-ai-gateway` 定位差异较大，只有明确产品方向需要时才推进。

## new-api-main 对照

| 功能面 | 参考项目能力 | x-ai-gateway 状态 | 是否完全 | 说明 |
| --- | --- | --- | --- | --- |
| 多协议网关入口 | OpenAI-compatible、Responses、Claude Messages、Gemini、Realtime、Rerank、图像、音频、视频等 | 基本实现 | 否 | 已有 OpenAI/Anthropic/Gemini/Ollama、多类 OpenAI 入口、Realtime、Files、Batches、Images、Audio、Uploads、异步 Video/Music；未见 Rerank、Dify、Midjourney、Suno 等同等广度。 |
| Provider 生态 | 大量 channel adapter 与模型常量 | 部分实现 | 否 | 当前 `provider-catalog.json` 首版只有 OpenAI、Azure OpenAI、DeepSeek、OpenRouter、Anthropic、Gemini；Marketplace 有签名更新，但生态宽度远低于 new-api。 |
| 格式转换 | OpenAI 与 Claude/Gemini/Responses 互转 | 基本实现 | 否 | Canonical/mapper 体系已覆盖主链路，但部分参考能力仍处于“兼容/降级/矩阵说明”级别，未达到全协议全字段 parity。 |
| 智能路由 | 权重、重试、限流、渠道健康 | 基本实现到较完善 | 接近但否 | x-ai-gateway 的 Route Policy、Redis runtime store、熔断、限流、治理和观测更偏企业控制面；但 provider 宽度和真实压测证据仍不足。 |
| 计费与支付 | 在线充值、Stripe/易支付、模型计价、缓存计费 | 部分实现 | 否 | 已有订单、余额流水、provider webhook 签名验签与幂等入账；缺真实支付下单、退款、争议、主动对账、支付服务商实例路由和完整前台收银体验。 |
| OAuth/安全 | Discord、LinuxDO、Telegram、OIDC、Passkey/2FA 等 | 基本实现 | 否 | x-ai-gateway 已支持 Google、QQ、WeChat、GitHub、Meta、X 社交 OAuth，另有 Passkey/TOTP/验证码/邮箱验证；但 OIDC/Discord/LinuxDO/Telegram 不是同等覆盖，真实线上 smoke 仍需逐 provider 留证。 |
| UI/i18n | 多语言 UI、公开文档、可视化面板 | 部分实现 | 否 | 管理端页面丰富，但 UI 不是完整多语言；公开 docs bundle 只有基础 zh-CN/en-US；README 仍残留 Notion 旧事实源。 |
| 部署与运维 | Docker/Compose、多机、环境变量文档 | 基本实现 | 否 | 已有 Dockerfile、Compose、PowerShell install/upgrade/rollback、预检接口；生产级 Linux 一键脚本、在线升级 UI、长期运维文档仍不如参考项目完整。 |
| One API 数据兼容 | 兼容 One API 数据库 | 未实现/条件需求 | 否 | 当前项目采用自有 Spring/Postgres/Liquibase 模型。只有需要迁移 One API 用户时才建议做。 |

## sub2api-main 对照

| 功能面 | 参考项目能力 | x-ai-gateway 状态 | 是否完全 | 说明 |
| --- | --- | --- | --- | --- |
| 多账号/订阅账号管理 | OAuth/API Key 多账号、账号组、授权流 | 基本实现 | 否 | 已有 Account、Account Pool、OAuth session、编程类账号身份治理、quota/health 字段；但与 Sub2API 针对 Claude/Codex/Gemini 官方订阅账号的细粒度运营仍有差距。 |
| API Key 分发 | 用户自助生成 Key、用量和额度控制 | 基本实现 | 接近但否 | Distributed Key、Access Group、Portal 自助、一次性 secret、客户端接入包已形成闭环；仍需更完整的用户侧购买/套餐/收银链路。 |
| 精确计费 | Token 级用量、成本、余额、清理和导出 | 基本实现 | 否 | usage normalizer、账务 rollup、CSV 导出已存在；缺持久化日级 rollup、历史清理/归档、全 provider 价格同步和长周期账务校验。 |
| 智能调度 | 账号选择、粘性会话、失败回退 | 基本实现 | 否 | AccountSelectionService 支持 Redis sticky，路由策略支持熔断/限流；但 Sub2API 的账号运营、批量测试、监控模板、实时可用渠道选择仍更细。 |
| 并发/速率限制 | 用户级、账号级并发与请求/Token 限制 | 基本实现 | 否 | Access Group/Distributed Key 有 rpm、tpm、concurrencyLimit 字段；Route Policy 有 Redis rate window。并发占用释放、账号级并发水位和压测证据仍需要补齐。 |
| 支付系统 | EasyPay、支付宝官方、微信官方、Stripe，多实例、退款、限额、前台路由 | 部分实现 | 否 | 当前只完成订单状态机和 webhook 入账；支付 provider 生产能力明显弱于 Sub2API。 |
| 管理后台与运营 | 用户、账号、渠道监控、支付、公告、外部系统 iframe | 基本实现 | 否 | 管理端模块很丰富，已有 external app/runtime；但成熟度、支付运营、批量导入/导出、移动端生态仍不足。 |
| 部署/升级 | Linux install.sh、systemd、Docker 多模式、在线升级/回滚 | 部分到基本实现 | 否 | x-ai-gateway 更偏 Windows/PowerShell 私有化部署脚本；缺 Linux systemd 一键安装和后台在线升级体验。 |
| 数据管理 | datamanagementd、备份迁移、导入导出 | 部分实现 | 否 | 已有备份、checkpoint、upgrade/rollback dry-run 和 smoke；但独立数据管理服务与完整迁移工具链不足。 |

## cc-switch-main 对照

| 功能面 | 参考项目能力 | x-ai-gateway 状态 | 是否完全 | 说明 |
| --- | --- | --- | --- | --- |
| 多 CLI 管理 | Claude Code、Codex、Gemini CLI、OpenCode、OpenClaw、Hermes 配置管理 | 部分实现 | 否 | x-ai-gateway 是服务端网关，不是桌面配置工具；已提供 5 类 CLI onboarding pack，但不直接读写本机配置，也未覆盖 Hermes。 |
| Provider 预设与一键切换 | 50+ 预设、拖拽排序、系统托盘切换 | 部分实现/不完全同域 | 否 | 服务端有 Provider Catalog 和 Marketplace，但预设数量、客户端配置写入、桌面切换体验不足。 |
| 本地代理热切换 | 本地 proxy、格式转换、故障转移、熔断、健康监控 | 基本实现但场景不同 | 否 | x-ai-gateway 在服务端路由侧实现了重试/熔断/健康/格式适配；不具备桌面本地代理接管与托盘热切换。 |
| MCP/Prompts/Skills | 统一管理、双向同步、Deep Link 导入、Skills 安装 | 部分实现 | 否 | onboarding pack 只提供 MCP/Prompts/Skills 示例文本；没有像 CC Switch 一样管理本机 MCP、Prompt 文件和 Skills 仓库。 |
| 用量与成本追踪 | 跨 provider/request/session 统计、定价编辑 | 基本实现 | 否 | x-ai-gateway 有服务端 usage/rollup/cost model；缺本地 session log 导入、客户端工具维度用量、同等 UI 细节。 |
| Session Manager | 浏览、搜索、恢复本地 CLI 会话 | 未实现 | 否 | x-ai-gateway 的 Live Session 是 Realtime/ops 管理概念，不是本地 Codex/Claude/Gemini 会话扫描与恢复。 |
| Workspace 编辑器 | 编辑 AGENTS.md、SOUL.md 等工作区文件 | 未实现/不建议短期照搬 | 否 | 这是桌面工具核心功能，不是网关必需。若要做“开发者客户端套件”，再拆分。 |
| 云同步/WebDAV | 多设备同步 provider/MCP/Skills 数据 | 未实现/不建议短期照搬 | 否 | 服务端网关自身不需要本机配置云同步；但如果做桌面 companion，需要单独规划。 |
| Deep Link | `ccswitch://` 导入 provider/MCP/prompt/skill | 部分实现 | 否 | onboarding pack 有非 secret deep link 概念，但没有对应原生客户端协议处理。 |
| 跨平台发布/i18n | Tauri、自动更新、签名、公证、多语言 | 未实现/不适用 | 否 | 当前不是桌面应用；只可作为未来客户端 companion 方向。 |

## 当前已达到的优势

- 服务端治理能力强：Route Policy、Redis runtime store、SLO、Ops、Incident、Change Plan、Recovery Checkpoint 等比参考项目更偏企业运维。
- 协议面已覆盖主流入口：OpenAI、Anthropic、Gemini、Ollama、Responses、Realtime、Files、Batches、Images、Audio、Uploads、Video/Music async task 均有入口或执行器。
- 本地闭环质量较高：大量后端单测、前端 Vitest、smoke harness、本地 docs/tasks 回写已经形成可追溯流程。
- 安全能力比早期网关更完整：Passkey、TOTP、验证码、邮箱验证、社交 OAuth、审计、一次性 secret grant 都已存在。

## 主要差距

| 优先级 | 差距 | 影响 |
| --- | --- | --- |
| High | Provider 生态广度不足 | 和 new-api/CC Switch 的 50+ provider/channel/preset 相比，当前 catalog 与真实 adapter 覆盖偏窄。 |
| High | 支付生产闭环不足 | 尚未具备 Sub2API 级别的真实下单、退款、争议、主动对账、多实例路由和前台收银体验。 |
| High | Realtime/Media 真实生产硬化不足 | WebSocket 目前更偏事件契约和 conformance，本地 Video/Music 有上游模式但缺多 provider 实战矩阵。 |
| Medium | 客户端生态不足 | 已能导出接入包，但没有管理本机 CLI 配置、MCP、Prompts、Skills、Session、Workspace。 |
| Medium | 文档/i18n/OpenAPI 不完整 | `README.md` 事实源过期，公开 docs bundle 与 UI i18n 不足，缺完整 OpenAPI/SDK 文档生成链。 |
| Low/Conditional | One API/Sub2API 数据迁移兼容 | 若目标用户已有 One API/Sub2API 数据，当前缺迁移工具；否则不是核心短板。 |

## 新增本地任务

- [TASK-20260506-009 Provider 生态广度与 Conformance 完善](../../tasks/backlog/TASK-20260506-009-provider-ecosystem-conformance.md)
- [TASK-20260506-010 支付生产闭环完善](../../tasks/backlog/TASK-20260506-010-production-payment-closure.md)
- [TASK-20260506-011 Realtime 与 Media 生产硬化](../../tasks/backlog/TASK-20260506-011-realtime-media-production-hardening.md)
- [TASK-20260506-012 客户端生态与本地工具链补齐](../../tasks/backlog/TASK-20260506-012-client-ecosystem-local-tooling.md)
- [TASK-20260506-013 文档、i18n 与 OpenAPI 事实源修复](../../tasks/backlog/TASK-20260506-013-docs-i18n-openapi-truth-source.md)

## 建议推进顺序

1. 先做 `TASK-20260506-009`，因为 provider catalog/conformance 是后续计费、路由、兼容性和公开文档的事实基础。
2. 然后做 `TASK-20260506-010`，把充值和余额从“webhook 入账骨架”推进到真实支付生产闭环。
3. 同步或第三步做 `TASK-20260506-011`，把 Realtime/Media 从“接口契约正确”推进到“真实 provider 可稳定跑”。
4. `TASK-20260506-012` 属于产品方向选择：如果要做开发者本地客户端 companion，则推进；如果只做服务端网关，可降级。
5. `TASK-20260506-013` 应尽快做一小轮，至少修复 README 旧 Notion 表述，并生成公开 API/兼容矩阵的单一事实源。
