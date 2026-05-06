# MIG-20260501 Notion/Linear 到本地文档任务迁移

状态：Done  
日期：2026-05-01  
关联需求：[REQ-20260501-001](../requirements/REQ-20260501-001-local-workflow-migration.md)  
关联报告：[REP-20260501](../reports/REP-20260501-open-source-gap-analysis.md)

## 迁移范围

本次迁移覆盖本轮对标分析产生的线上数据：

- Notion 页面：`x-ai-gateway：对标 new-api / Sub2API / CC Switch 的差距分析与增强任务（2026-05-01）`
- Notion URL：https://app.notion.com/p/35379afa4790819ba20bccc6e092eb0e
- Linear 父任务：X-281
- Linear 子任务：X-282 到 X-289
- Linear 评论：2 条
- Linear 未创建任务：4 个，因免费 issue 数量限制未能生成独立 issue

## 本地落点

- Notion 分析正文迁移到：[REP-20260501](../reports/REP-20260501-open-source-gap-analysis.md)
- Linear 父任务迁移到：[TASK-20260501-000](../../tasks/backlog/TASK-20260501-000-gap-enhancement-overview.md)
- Linear 子任务迁移到：
  - [TASK-20260501-001](../../tasks/backlog/TASK-20260501-001-provider-registry-2.md)
  - [TASK-20260501-002](../../tasks/backlog/TASK-20260501-002-non-chat-resources.md)
  - [TASK-20260501-003](../../tasks/backlog/TASK-20260501-003-realtime-streaming-proxy.md)
  - [TASK-20260501-004](../../tasks/backlog/TASK-20260501-004-billing-payment-loop.md)
  - [TASK-20260501-005](../../tasks/backlog/TASK-20260501-005-portal-self-service.md)
  - [TASK-20260501-006](../../tasks/backlog/TASK-20260501-006-programming-account-identity.md)
  - [TASK-20260501-007](../../tasks/backlog/TASK-20260501-007-client-onboarding-pack.md)
  - [TASK-20260501-008](../../tasks/backlog/TASK-20260501-008-routing-policy-2.md)
- 未创建的 Linear 任务迁移到：
  - [TASK-20260501-009](../../tasks/backlog/TASK-20260501-009-security-system.md)
  - [TASK-20260501-010](../../tasks/backlog/TASK-20260501-010-production-deployment-upgrade.md)
  - [TASK-20260501-011](../../tasks/backlog/TASK-20260501-011-monitoring-billing-rollup.md)
  - [TASK-20260501-012](../../tasks/backlog/TASK-20260501-012-i18n-public-docs-compatibility.md)

## Linear 评论迁移

### 评论 1

已完成对标分析的 Notion 文档与首批 Linear 任务创建。

Notion 文档：
https://app.notion.com/p/35379afa4790819ba20bccc6e092eb0e

已创建子任务：

- X-282 Provider Registry 2.0：插件化供应商目录、预设导入与厂商元数据
- X-283 非 Chat 资源族扩展：Rerank / Video / Music / Task async lifecycle
- X-284 Realtime 与 Streaming 真实代理闭环：WebSocket/SSE、事件映射和 conformance
- X-285 SaaS 计费与支付闭环：价格、余额、订单、支付渠道、Webhook、对账
- X-286 Portal 用户自助增强：用量、账单、渠道状态、个人资料、订单与支付
- X-287 编程类账号身份治理：Codex / Antigravity / Copilot / Claude Plan OAuth 与额度同步
- X-288 客户端接入包：Codex/Claude/Gemini CLI 配置导出、Deep Link、MCP/Prompts/Skills
- X-289 路由策略 2.0：权重、自动 retry、fallback、熔断、用户/模型/账号限流可视化

待创建但本次未成功创建的任务：

- 安全体系增强：OIDC、2FA/Passkey/TOTP、验证码、SSRF、敏感词与注册策略
- 生产部署与升级体系：Docker Compose、.env.example、install 脚本、在线升级/回滚
- 监控与账务 rollup：长周期用量聚合、清理、导出、渠道健康日报
- 国际化、公开文档与兼容性样例：OpenAPI、SDK 示例、多语言 UI/Docs

原因：Linear 返回 `Usage limit exceeded - You've exceeded the free issue limit for this workspace.`，说明当前 workspace 已达到免费 issue 数限制。上述 4 个条目已保留在 Notion 分析文档中，待配额恢复后可补建。

### 评论 2

补建重试记录：

尝试继续创建以下 4 个待建任务：

- 安全体系增强：OIDC、2FA/Passkey/TOTP、验证码、SSRF、敏感词与注册策略
- 生产部署与升级体系：Docker Compose、.env.example、install 脚本、在线升级/回滚
- 监控与账务 rollup：长周期用量聚合、清理、导出、渠道健康日报
- 国际化、公开文档与兼容性样例：OpenAPI、SDK 示例、多语言 UI/Docs

结果：全部仍被 Linear 拒绝创建。

返回错误：`Usage limit exceeded - You've exceeded the free issue limit for this workspace. Please upgrade or contact sales@linear.app for a free trial.`

判断：当前限制是 workspace 免费计划的 issue 数量上限，不是普通 API rate limit。待升级计划、释放/归档/删除旧 issue，或联系 Linear 获取 trial 后，可继续按 Notion 文档补建。

## 迁移后的处理规则

- 后续默认不再向线上 Notion/Linear 写入数据。
- 已迁移内容以本地 Markdown 为准。
- 若线上数据后续发生变化，需要另开迁移记录。

## 线上清理记录

清理时间：2026-05-01

### Notion

已处理页面：

- 原页面：`x-ai-gateway：对标 new-api / Sub2API / CC Switch 的差距分析与增强任务（2026-05-01）`
- URL：https://app.notion.com/p/35379afa4790819ba20bccc6e092eb0e

处理方式：

- 页面标题改为：`已迁移到本地：x-ai-gateway 对标差距分析与增强任务`
- 页面正文替换为本地迁移占位说明。
- 原始正文已经迁移到：[REP-20260501](../reports/REP-20260501-open-source-gap-analysis.md)

说明：当前 Notion 连接器未暴露物理删除/归档页面接口，因此采用“清空正文并标记已迁移”的方式，让线上页面不再作为事实来源。

### Linear

已处理 issue：

- X-281
- X-282
- X-283
- X-284
- X-285
- X-286
- X-287
- X-288
- X-289

处理方式：

- 将 issue 标题改为 `已迁移到本地：...`。
- 将 issue 描述替换为本地任务、本地报告和迁移记录链接。
- 将 issue 状态置为 canceled 类型状态。
- 删除迁移过程中追加的两条 Linear 评论，避免线上保留正文副本。

说明：当前 Linear 连接器未暴露物理删除 issue 的接口，因此采用“占位描述 + canceled 状态”的方式停止线上跟踪。本地 `tasks/backlog/` 中的任务仍然有效。
