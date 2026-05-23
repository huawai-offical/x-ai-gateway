# REQ-20260522-023 全局操作结果提示收敛

状态：Done  
日期：2026-05-22  
关联任务：[TASK-20260522-024](../../tasks/done/TASK-20260522-024-action-feedback-toast-coverage-parent.md)、[TASK-20260522-024-01](../../tasks/done/TASK-20260522-024-01-react-query-action-feedback-defaults.md)

## 背景

用户反馈“凭证信息里面，刷新模型的时候，成功没有提示，失败有”，并进一步要求“不管成功还是失败都需要提示，不止这一个界面的操作如此，所有操作都需要”。当前项目上一轮已把 `InlineError` 统一为左上角 toast，但成功提示仍主要依赖各页面自行编写，导致凭证详情刷新模型等 mutation 成功后只有局部文案或数据变化，没有明确的操作完成反馈。

## 目标

- 所有通过 React Query mutation 发起的用户操作默认具备成功 toast。
- 所有通过 React Query mutation 发起的用户操作默认具备失败 toast。
- 保留页面已有的业务状态更新、局部结果面板和 `InlineError` 兜底，但避免失败提示重复弹出。
- 凭证信息中的“刷新模型”成功后必须有明确提示。

## 范围

- `web/src/app/query-client.ts`
- `web/src/lib/typed-react-query.ts`
- `web/src/components/app/inline-error.tsx`
- 新增前端 action feedback 工具与测试。
- 对凭证刷新模型补充更明确的成功文案。

## 非目标

- 不在本轮迁移所有 `window.confirm`，该范围已由 `TASK-20260522-004` 承接。
- 不重写所有页面局部状态展示。
- 不改变后端 API 契约、刷新模型接口或凭证数据结构。
- 不把自动加载 query 的成功结果弹 toast；本轮只覆盖用户触发的 mutation 类操作。

## 方案

1. 在前端新增统一 action feedback 工具，负责标准化成功、失败 toast 文案、错误详情和 `traceId` 展示。
2. 在应用级 `QueryClient` 的 `MutationCache` 中接入默认成功/失败 toast，使现役 mutation 操作不再依赖每个页面手写提示。
3. 对已由全局 mutation 失败提示处理过的错误打标，`InlineError` 遇到该错误时不再重复弹 toast。
4. 扩展 `useTypedMutation` 的本地类型，允许页面通过 `meta.actionName`、`meta.successMessage`、`meta.errorMessage` 定制提示文案。
5. 给凭证页刷新模型 mutation 补充 `successMessage`，显示发现模型数量。

## 风险

- 全局 mutation 成功提示会让过去“静默成功”的操作都弹出提示，需要保持文案克制。
- 失败提示若和 `InlineError` 同时存在，必须去重，否则用户会看到重复错误。
- 测试中大量自建 `QueryClient` 不一定启用应用级 `MutationCache`，需要通过定向单元测试覆盖统一工具和应用级 query client。

## 验收标准

1. 凭证信息“刷新模型”成功后出现成功 toast，失败后出现失败 toast。
2. 其他 React Query mutation 操作即使页面未手写成功提示，也默认出现“操作成功”类 toast。
3. mutation 失败已由全局提示展示时，`InlineError` 不再重复弹出同一错误。
4. 支持页面通过 mutation `meta` 自定义操作名称、成功文案、失败文案。
5. 前端类型检查和定向测试通过。

## 实现结果

- 新增 `web/src/components/app/action-feedback.ts`，统一封装 mutation 成功、失败 toast，并支持 `actionName`、`successMessage`、`errorMessage`、`suppressSuccessToast`、`suppressErrorToast`。
- `web/src/app/query-client.ts` 已接入 `MutationCache`，应用级 React Query mutation 成功默认显示“操作成功”，失败默认显示“操作失败”并带错误详情。
- `web/src/components/app/inline-error.tsx` 已识别全局 action feedback 标记，避免 mutation 失败同时由 `MutationCache` 和 `InlineError` 重复弹出。
- `web/src/lib/typed-react-query.ts` 已补充 mutation `meta` 类型，页面可按操作定制提示文案。
- `web/src/features/credentials/credentials-page.tsx` 的“刷新模型”已配置成功 toast：`模型刷新完成：发现 N 个模型。`
- `web/src/features/auth/auth-settings-page.tsx` 已对已有手写成功提示设置 `suppressSuccessToast`，避免控制台凭证更新后重复提示。
- 顺手修正 Codex auth.json 导入请求体构造，`sourceLabel` 继续只作为失败展示字段，不再通过未使用解构触发 lint。

## 测试/验证

- `bun run typecheck`：通过。
- `bun run test -- --run src/components/app/action-feedback.test.ts src/components/app/inline-error.test.tsx src/app/query-client.test.ts src/features/credentials/credentials-page.test.tsx`：通过，4 个测试文件、13 条测试。
- `bunx eslint src/components/app/action-feedback.ts src/components/app/action-feedback.test.ts src/components/app/inline-error.tsx src/components/app/inline-error.test.tsx src/app/query-client.ts src/app/query-client.test.ts src/lib/typed-react-query.ts src/features/credentials/credentials-page.tsx src/features/auth/auth-settings-page.tsx`：通过。
- 浏览器验证 `http://localhost:5173/login?reason=logged-out&redirect=/console/credentials`：前端页面可加载，未出现 Vite/React 错误覆盖，toast 容器为 `data-x-position="left"`、`data-y-position="top"`。
- 浏览器验证限制：当前本地后端返回 5xx，登录挑战无法完成，因此未执行真实凭证页按钮点击；已有单元测试覆盖凭证页刷新模型成功文案，浏览器只验证前端容器和运行状态。

## 遗留问题

- `window.confirm` 迁移仍由既有 [TASK-20260522-004](../../tasks/backlog/TASK-20260522-004-confirm-dialog-notification-unification.md) 承接，本轮未处理。
- 当前统一策略覆盖 React Query mutation。纯 query 重新加载、导航、筛选、表格分页等非写操作不弹成功 toast，避免普通浏览行为产生噪音。
