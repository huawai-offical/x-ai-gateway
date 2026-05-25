# REQ-20260521-007 现役 UI 全界面汉化与说明性文案收口

状态：In Progress  
日期：2026-05-21  
上游来源：用户指令“还有 UI 界面只需要支持中文，全界面汉化，除技术术语可以使用 English 外，都要汉化，交给 subagent 处理”

## 背景

当前前端现役界面仍混杂较多英文或半英文展示，包括导航、页面标题、按钮、状态文案、空状态、Dialog 辅助说明、Portal/Public 页面介绍文字，以及一批为解释产品边界而新增的说明性正文。

用户明确要求：

1. 现役 UI 默认只支持中文展示。
2. 全界面汉化，只有必要技术术语可以保留 English。
3. 不再保留冗余解释性文案，页面只保留必要标题、操作、状态、错误反馈和数据内容。
4. 本轮工作应拆给 subagent 并行处理。
5. 字段标签、步骤标题和 mixed/camelCase 展示文案也要汉化，例如 `requestId` 应展示为 `请求 ID`。
6. 技术名词与业务中文混排时，也应优先以自然中文展示，例如 `访问 Key` 应展示为 `访问密钥`、`Trace` 应展示为 `链路` 或 `链路追踪`。

## 目标

- 将现役 UI 中的静态展示文案统一收敛为中文。
- 对必须保留的技术术语建立最小白名单，例如 API、Key、Secret、Token、OAuth、Provider、Smoke、Webhook、runtime、OpenAI、Codex。
- 将直接暴露给用户的字段标签、步骤标题、camelCase 标识和半英文文案改写为中文展示，而不是原样暴露内部命名。
- 删除现役页面中仅用于解释产品概念、页面边界或设计取舍的说明性正文。
- 在不改动后端 API 契约的前提下，提升前端中文一致性与界面收敛度。
- 将技术名词与业务中文的混排口径继续收紧，避免出现“中文句子里夹旧英文标签”的割裂感。

## 范围

- `web/src/app/` 下导航、路由表、页面壳层和共享路由描述。
- `web/src/features/` 下现役 Console、Portal、Public、Workbench 页面中的静态展示文案。
- Dialog、Alert、EmptyState、PageSection 标题、副标题、按钮、筛选提示、默认占位文案。
- 与本轮页面文案相关的前端测试断言。

## 非目标

- 本轮不改动后端 API 字段名、数据库字段名和返回结构。
- 本轮不翻译用户输入、Provider 原始返回内容、模型 ID、错误堆栈和日志原文。
- 本轮不引入 i18n 多语言框架；当前要求是界面默认中文，而不是增加中英切换。
- 本轮不机械翻译必须保留英文语义的技术术语、品牌名和协议名。

## 风险

- 如果技术术语白名单不清晰，可能把 `API`、`Key`、`OAuth` 等必要术语误译，反而影响专业用户理解。
- 如果把 `requestId`、`gatewayResourceKey`、`Client Instance`、`Step 1` 这类面向用户的展示标签误留为内部命名，会造成“看起来已汉化，实际仍然半英文”的割裂感。
- 如果保留 `访问 Key`、`Key 停用`、`查看 Trace` 等旧式混排标签，会让界面显得只做了表层翻译，削弱整体一致性。
- 如果把运行反馈、错误提示或数据标签误删为“说明性文案”，会损伤可用性。
- 如果只改页面正文、不改测试断言和共享文案，容易留下大量半中文半英文残留。

## 验收标准

1. 现役导航、页面标题、按钮、空状态、Dialog/Alert 说明、静态占位文案默认使用中文展示。
2. 仅技术术语、品牌名、协议名和用户数据允许保留 English。
3. 用户可见的字段标签、步骤文案和内部标识展示已汉化，例如 `requestId` -> `请求 ID`、`Step 1` -> `步骤 1`。
4. 技术名词与业务中文混排场景也完成收口，例如 `访问 Key` -> `访问密钥`、`Key 停用` -> `访问密钥已停用`、`查看 Trace` -> `查看链路`。
5. 当前控制台中与“官方账号 / 上游凭证边界”相关的解释性正文不再展示。
6. 前端检索结果中不再残留明显不必要的英文 UI 静态文案。
7. 前端 `bun run typecheck` 通过；必要时补充定向 vitest 校验。

## 测试边界

- 代码检索：按页面检索明显英文静态文案、旧解释性正文和相关断言。
- 前端：`bun run typecheck`
- 前端：针对改动页面的定向 vitest
- 手工检查：重点验证导航、凭证、账号分组、Portal/Public 入口等用户高频页面

## 关联文档

- [REQ-20260521-004](./REQ-20260521-004-upstream-credential-entry-and-official-account-clarity.md)
- [REQ-20260520-001](./REQ-20260520-001-ui-ux-console-portal-experience.md)

## 当前进展

- 已用 subagent 做过一轮 `web/src` 静态文案扫描，并把高信号残留转成主线程可执行修正项。
- 已完成控制台高频链路的第二轮深度汉化，包括 `request logs`、`traces`、`upstream cache`、`portal home`、`Codex onboarding`、`dashboard`、`incidents`、`credentials`、`OAuth connect/callback`、`resources`、`governance`、`windows` 等页面。
- 已删除 `dashboard`、`incidents`、`resources`、`ops alerts` 等页面中的解释性正文或迁移提示，避免继续暴露“页面为何这样设计”的说明文案。
- 已把 `requestId`、`gatewayResourceKey`、`providerType`、`distributedKeyId`、`Session Key`、`API Key / Secret` 等用户可见口径继续收紧为中文或更自然的中英混排。
- 已补齐协议入口新增/编辑弹窗“2. 运行时策略”页签汉化，将 `Provider Type`、`Site Kind`、`Auth Strategy`、`Path Strategy`、`Model Addressing`、`Error Schema`、`Stream Transport` 等可见字段标签收口为中文，同时保留底层枚举值和 API payload。
- 当前仍保留 In Progress，用于继续承接 Console、Portal/Public/Workbench 中尚未完全收口的零散残留。

## 已验证范围

- 通过：`bun run typecheck`
- 通过：`bun run vitest run src/app/navigation.test.ts src/app/operations-router.test.tsx src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-page.test.tsx src/features/incidents/incidents-page.test.tsx src/features/request-logs/request-logs-page.test.tsx src/features/accounts/codex-onboarding-page.test.tsx src/features/accounts/oauth-connect-page.test.tsx src/features/portal/portal-home-page.test.tsx src/features/traces/traces-page.test.tsx src/features/upstream-cache/upstream-cache-page.test.tsx src/features/credentials/credentials-page.test.tsx src/features/resources/resources-page.test.tsx src/features/ops/ops-alerts-page.test.tsx src/features/ops/governance-page.test.tsx src/features/operations/windows-page.test.tsx`
- 通过：`bun run test -- src/features/provider-sites/provider-site-detail-page.test.tsx`
