# REP-20260505 Notion 文档回迁摘要

状态：Done
日期：2026-05-05
关联需求：[REQ-20260505-001](../requirements/REQ-20260505-001-notion-linear-back-migration.md)
关联迁移记录：[MIG-20260505](../migrations/MIG-20260505-notion-linear-back-migration.md)

## 背景

用户要求将线上 Notion 文档回迁到本地。当前仓库已经切换为本地优先协作流程，因此本报告记录 2026-05-05 可读取到的 Notion `x-ai-gateway` 相关页面、主要内容和本地落点。

## 查询范围

- `x-ai-gateway`
- `对标 new-api Sub2API CC Switch`
- `已迁移到本地 x-ai-gateway`

## 已读取页面

| Notion 页面 | 线上 ID | 本地处理 |
| --- | --- | --- |
| 已迁移到本地：x-ai-gateway 对标差距分析与增强任务 | `35379afa4790819ba20bccc6e092eb0e` | 确认 2026-05-01 已迁移到 [REP-20260501](REP-20260501-open-source-gap-analysis.md) |
| x-ai-gateway：当前文档索引与入口 | `33b79afa47908138aa54c1ae771ad264` | 摘要回迁到本报告 |
| x-ai-gateway：详细 WBS 与任务分期 | `33a79afa47908174ba2bcdf4282d522d` | 摘要回迁到本报告 |
| x-ai-gateway：多协议 AI 网关规划与任务沉淀（2026-04-06） | `33a79afa479081b29be2d62b05c15fe9` | 摘要回迁到本报告 |
| x-ai-gateway：10 设计文档 | `34179afa47908192a78bf4548a42dc2d` | 摘要回迁到本报告 |
| x-ai-gateway：20 规划与 WBS | `34179afa479081d4846ff1dc59cebe8e` | 摘要回迁到本报告 |
| x-ai-gateway：30 开发进展 | `34179afa47908129b76df61ec73b9aa6` | 摘要回迁到本报告 |
| x-ai-gateway：40 关单与回写 | `34179afa479081d9bc69fbcc6963fe68` | 摘要回迁到本报告 |
| x-ai-gateway：全厂商自动翻译闭环（主流厂商全覆盖，2026-04-11） | `33f79afa4790816e998ddccc9da7e` | 摘要回迁到本报告 |
| x-ai-gateway：Spring AI 抽象边界与网关自建层说明 | `33a79afa479081f980feefa24f5fd9fa` | 摘要回迁到本报告 |
| x-ai-gateway：协议无感自动路由与全自动互转 V2 实施计划（2026-04-14） | `34279afa479081128a08cf9ba7501a64` | 摘要回迁到本报告 |
| x-ai-gateway vs ai-gateway 功能差距深度审计 - 2026-04-24 | `34c79afa4790814e94a1c52aa64307a3` | 摘要回迁到本报告，并与 Linear X-242/X-263 对齐 |

## 回迁摘要

### 当前文档入口

Notion 侧曾按 `00 总览与导航 / 10 设计文档 / 20 规划与 WBS / 30 开发进展 / 40 关单与回写` 组织项目文档。当前本地仓库采用 `docs/requirements`、`docs/decisions`、`docs/reports`、`docs/migrations` 与 `tasks/` 替代该线上目录树。

### 项目定位

`x-ai-gateway` 是 Spring Boot 多协议 AI 网关，目标是通过一个前台 DistributedKey 访问 OpenAI、Anthropic、Gemini 等多协议入口，并根据模型、能力、健康度、配额、成本、权重和策略自动路由到合适上游。`ai-gateway` 是参考项目，不是当前仓库。

### Spring AI 边界

Notion 设计明确：Spring AI 适合复用在 provider adapter 的模型调用、Prompt/Message/ChatResponse/Usage 与 options 容器层；多协议解析/回渲染、Gateway IR、路由、缓存、Key affinity、cache benefit usage、`/interop/plan`、route explanation 仍由网关自建。

### 全厂商自动翻译闭环

2026-04-11 的主线将目标扩展为 OpenAI、OpenAI-compatible、Anthropic、Gemini、Ollama、Azure OpenAI、DeepSeek、Grok、Mistral、Cohere、Together、Fireworks、OpenRouter、Vertex AI 等主流站点的能力快照、站点档案、错误语义、冷却/fallback、conformance harness 与控制面闭环。对应 Linear 主线为 X-152 与 X-154 至 X-162。

### 协议无感自动路由 V2

2026-04-14 的 V2 方案使用 `Ingress Adapter -> Canonical IR -> Execution Planner -> Provider Adapter -> Egress Renderer`，并明确 Spring AI 是执行后端之一，不是语义真相源。执行模式包括 `CANONICAL_SPRING_AI` 与 `RAW_PASSTHROUGH`。对应 Linear 主线为 X-166 与 X-167 至 X-173。

### ai-gateway 差距审计

2026-04-24 的差距审计指出后续增强方向包括用户门户、Access Group/套餐权益/Key 授权继承、公告中心、兑换码/余额流水、OAuth/Session 账号治理、分发 Key 客户端配置导出、外部应用扩展、维护控制面真实执行、Native 协议命名空间、Realtime/Live Session、Ops Probe Run/系统事件、成本路由。对应 Linear 主线为 X-242 与 X-243 至 X-254，后续第二轮主线为 X-263。

## 本地落点

- 线上 Notion 页面树的当前可读摘要落到本报告。
- 2026-05-01 对标分析原文仍以 [REP-20260501](REP-20260501-open-source-gap-analysis.md) 为本地事实来源。
- Linear 全量任务回迁落到 [LINEAR-20260505](../migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md)。

## 遗留问题

- Notion 连接器本轮用于读取和 fetch；未执行线上删除或清空。
- 部分 Notion 页面正文较长，本报告保留索引、主线和关键结论；如后续需要逐页全文归档，可在 `docs/migrations/notion-pages/` 下继续拆分。
