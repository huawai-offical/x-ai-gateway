# TASK-20260521-007-03 运维观测链路界面汉化

状态：已完成  
上游来源：[TASK-20260521-007](../in-progress/TASK-20260521-007-ui-chinese-only-localization.md)

## 背景

Dashboard、Ops、Operations、Traces、Incidents 等页面仍存在中英混杂的标题、状态文案、说明性正文与默认占位，影响运维观测链路的一致性。

## 目标

- 汉化运维观测链路中的静态 UI 文案。
- 删除仅用于解释页面边界或产品取舍的说明性正文。
- 保留必要技术术语、状态反馈、错误提示与数据内容。

## 非目标

- 不改动凭证、账号分组、Portal/Public 页面。
- 不翻译后端返回的原始日志、错误文本与用户数据。

## 输入

- `web/src/features/dashboard/`
- `web/src/features/ops/`
- `web/src/features/operations/`
- `web/src/features/traces/`
- `web/src/features/incidents/`
- `web/src/features/error-rules/`

## 输出

- 运维观测链路页面中文化结果

## 验收标准

- [x] 相关现役页面静态 UI 文案默认中文化。
- [x] 说明性正文按需删除或收口。
- [x] 页面相关测试断言同步通过。

## 测试边界

- 检索相关目录中的明显英文静态文案
- 定向前端类型检查与测试

## 当前状态

已完成

## 实现结果

- 已在 `dashboard`、`ops`、`operations`、`traces`、`incidents` 范围内将标题、副标题、按钮、空状态、默认占位和明显英文状态短语收口为中文默认文案。
- 已删除或清空仅用于解释页面边界、步骤意图或产品取舍的说明性正文，主要体现在若干 `DialogDescription` 和说明块。
- 已在同一范围内清理锁死浅色主题的硬编码背景/文字/边框颜色，统一改为 `bg-card`、`bg-background`、`bg-muted`、`text-foreground`、`text-muted-foreground`、`border-border` 等主题 token 兼容写法，以适配全局 `dark` 默认和 `dark/light` 切换。

## 验证结果

- 通过：`bun run test src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-alerts-page.test.tsx src/features/ops/governance-page.test.tsx src/features/incidents/incidents-page.test.tsx src/features/ops/ops-page.test.tsx src/features/ops/ops-logs-page.test.tsx src/features/ops/system-events-page.test.tsx`
- 通过：`bun run typecheck`
- 备注：定向测试过程中 `recharts` 在 jsdom 下仍会输出容器宽高为 0 的 stderr 提示，但测试结果为通过，非本轮回归失败。

## 2026-05-21 第二轮补充

- 已完成首轮汉化与主题兼容；当前重新打开，继续承接运维观测链路中的深度汉化残留。
- 本轮重点补齐用户可见 mixed-case / 技术词混排口径，例如 `Trace`、`requestId`、`访问 Key` 等展示标签，并配合总览导航收口移除重复叙事。

## 2026-05-21 收尾结果

- 已补齐 `dashboard`、`incidents`、`traces`、`request logs`、`ops`、`upstream cache` 等页面的第二轮深度汉化，覆盖 `请求 ID`、`网关资源键`、`上游对象 ID`、`访问密钥 ID`、`提供方类型`、`链路追踪` 等直接面向用户的标签。
- 已删除 `dashboard` 与 `incidents` 页面顶端的解释性说明卡，仅保留功能入口与必要动作，符合“不要解释产品边界”的最新口径。
- 已补齐 `layout` 测试中的控制台会话 bootstrap 场景，并为控制台搜索弹窗补上无障碍描述，消除本轮相关 warning。
- 后续如果继续发现其他页面的汉化残留，由父任务 [TASK-20260521-007](../in-progress/TASK-20260521-007-ui-chinese-only-localization.md) 继续承接。
