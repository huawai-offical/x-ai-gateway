# TASK-20260521-007 现役 UI 全界面汉化与说明性文案收口

状态：进行中  
优先级：High  
上游来源：[REQ-20260521-007](../../docs/requirements/REQ-20260521-007-ui-chinese-only-localization.md)

## 任务类型

父任务

## 背景

现役前端界面仍存在大量英文或半英文静态文案，以及部分只用于解释产品概念和页面边界的说明性正文。用户要求将界面默认语言统一为中文，仅保留必要技术术语 English，并将工作拆给 subagent 并行处理。

## 目标

- 审计现役 UI 中的静态英文/半英文文案。
- 审计现役 UI 中直接暴露给用户的字段标签、步骤文案和 camelCase / mixed-case 展示内容。
- 按 Console、Portal/Public/Workbench 等表面拆分并行改造。
- 删除与产品概念解释相关的冗余说明性文案。
- 保持错误提示、运行反馈、用户数据和技术术语可读。

## 非目标

- 不改动后端 API 契约与数据库结构。
- 不引入多语言切换框架。
- 不翻译模型 ID、Provider 品牌名、原始错误日志和用户输入内容。

## 输入

- `web/src/app/`
- `web/src/features/`
- 当前前端测试文件
- [REQ-20260521-004](../../docs/requirements/REQ-20260521-004-upstream-credential-entry-and-official-account-clarity.md)

## 输出

- 汉化后的现役 UI 静态文案。
- 删除解释性正文后的页面与弹窗。
- 更新后的需求、任务与索引记录。

## 影响范围

- Console 导航与现役管理页面
- Portal/Public/Workbench 用户界面
- 前端测试断言与静态检索基线

## 依赖

- 当前前端路由与页面结构
- subagent 并行盘点结果

## 风险

- 页面多、文案散，容易遗漏共享组件或测试断言。
- 技术术语边界不一致会造成翻译风格漂移。
- 误删错误反馈或状态说明会影响使用与排障。

## 验收标准

- [ ] 现役 UI 静态展示文案默认中文化。
- [ ] 仅技术术语、品牌名、协议名和用户数据保留 English。
- [ ] 用户可见的字段标签、步骤标题和内部标识展示也已汉化，例如 `requestId` -> `请求 ID`。
- [ ] 解释产品边界的冗余正文已删除。
- [ ] `bun run typecheck` 通过。
- [ ] 定向 vitest 通过。

## 测试边界

- 检索：静态英文 UI 文案与旧解释性文案
- 前端：`bun run typecheck`
- 前端：定向 vitest

## 关联任务

- [TASK-20260521-007-01](./TASK-20260521-007-01-console-admin-ui-chinese-localization.md)
- [TASK-20260521-007-02](./TASK-20260521-007-02-portal-public-workbench-ui-chinese-localization.md)
- [TASK-20260521-007-03](../done/TASK-20260521-007-03-ops-observability-ui-chinese-localization.md)
- [TASK-20260521-004](./TASK-20260521-004-upstream-credential-entry-and-official-account-clarity.md)

## 当前状态

进行中

## 2026-05-21 深度汉化补充

- 第二轮汉化标准不仅处理整句英文，也处理对用户直接可见的字段标签、步骤标题和内部命名残留。
- 典型样例包括：`requestId`、`gatewayResourceKey`、`Client Instance`、`Step 1`、`Signed Context Preview`。
- 继续收口技术名词与业务中文混排残留，例如 `访问 Key`、`Key 停用`、`查看 Trace`、`Client Instance` 等用户可见标签。

## 2026-05-21 当前进展

- 已完成 `request logs`、`traces`、`upstream cache`、`Codex onboarding`、`portal home` 等页面的第二轮深度汉化，重点把 `requestId`、`gatewayResourceKey`、`providerType`、`distributedKeyId`、`Client Instance`、`session / resume token` 等用户可见口径收成中文。
- 已删除 `dashboard` 与 `incidents` 顶部“已收口到主面板”的解释性正文，避免在总览链路中继续展示产品边界说明。
- 已回归 `layout`、`navigation`、`dashboard`、`ops`、`incidents`、`request-logs`、`codex-onboarding`、`portal-home`、`traces`、`upstream-cache` 定向测试。
- 当前父任务继续保留进行中，用于承接其余 Console、Portal/Public/Workbench 页面尚未彻底收口的汉化残留。
