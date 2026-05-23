# TASK-20260522-024 全局操作结果提示收敛父任务

状态：Done  
优先级：Critical  
来源：User Request  
上游来源：[REQ-20260522-023](../../docs/requirements/REQ-20260522-023-action-feedback-toast-coverage.md)

## 任务类型

父任务

## 背景

凭证信息中的“刷新模型”失败时会通过 `InlineError` toast 提示，但成功时没有稳定 toast。用户明确要求所有操作无论成功失败都需要提示，因此需要把提示责任从单个页面提升到前端操作基础设施层。

## 目标

- 建立 React Query mutation 成功/失败默认 toast 机制。
- 修复凭证刷新模型成功无提示的问题。
- 保持既有失败提示兜底，同时避免重复 toast。
- 明确后续未覆盖操作的边界。

## 非目标

- 不覆盖纯查询 query 成功提示。
- 不迁移原生确认弹窗。
- 不调整后端接口与业务返回结构。
- 不做全量页面文案重写。

## 输入

- 用户反馈：所有操作成功和失败都需要提示。
- `docs/requirements/REQ-20260522-003-toast-feedback-and-component-splitting.md`
- `web/src/app/query-client.ts`
- `web/src/components/app/inline-error.tsx`
- `web/src/features/credentials/credentials-page.tsx`

## 输出

- 全局 mutation action feedback 工具与应用级 `QueryClient` 接入。
- 凭证刷新模型成功提示。
- 去重后的失败提示兜底。
- 定向测试与文档回写。

## 影响范围

前端所有挂在应用级 `QueryClientProvider` 下的 React Query mutation 操作，以及仍使用 `InlineError` 展示错误的页面。

## 依赖

- `sonner` 作为全局 toast 组件。
- `@tanstack/react-query` 的 `MutationCache` 回调。
- 当前 `InlineError` 已经 toast 化。

## 风险

- 页面自定义成功提示和全局默认提示可能重复，需要对已有手写提示做适配。
- mutation 失败后页面仍渲染 `InlineError`，需要通过错误打标去重。
- 测试环境自建 query client 可能不带全局反馈，需要定向验证应用 query client。

## 验收标准

- [x] 凭证刷新模型成功后有成功 toast。
- [x] mutation 失败有失败 toast，且不会和 `InlineError` 重复。
- [x] 未显式配置文案的 mutation 也有默认成功提示。
- [x] 类型检查和定向测试通过。
- [x] 需求文档和任务状态完成回写。

## 测试边界

- `bun run typecheck`
- `bun run test -- --run src/components/app/action-feedback.test.ts src/components/app/inline-error.test.tsx src/app/query-client.test.ts src/features/credentials/credentials-page.test.tsx`
- 变更文件定向 eslint。
- 浏览器验证前端加载、toast 容器位置和 console 状态。

## 关联文档

- [REQ-20260522-023](../../docs/requirements/REQ-20260522-023-action-feedback-toast-coverage.md)
- [REQ-20260522-003](../../docs/requirements/REQ-20260522-003-toast-feedback-and-component-splitting.md)

## 关联任务

- [TASK-20260522-024-01](TASK-20260522-024-01-react-query-action-feedback-defaults.md)
- [TASK-20260522-003](../done/TASK-20260522-003-toast-feedback-and-component-splitting.md)

## 当前状态

已完成，待归档到 `tasks/done/`。

## 实现记录

- 新增 `web/src/components/app/action-feedback.ts`，统一管理 action success/error toast、错误详情、`traceId` 和失败去重标记。
- `web/src/app/query-client.ts` 通过 `MutationCache` 接入全局 mutation 成功/失败提示。
- `web/src/components/app/inline-error.tsx` 遇到已由 action feedback 展示过的错误时不再重复弹出。
- `web/src/features/credentials/credentials-page.tsx` 的“刷新模型”通过 `meta.successMessage` 显示模型数量。
- `web/src/features/auth/auth-settings-page.tsx` 对已有手写成功提示禁用全局重复成功提示。
- `web/src/lib/typed-react-query.ts` 已补齐 mutation `meta` 类型。

## 测试/验证

- `bun run typecheck`：通过。
- `bun run test -- --run src/components/app/action-feedback.test.ts src/components/app/inline-error.test.tsx src/app/query-client.test.ts src/features/credentials/credentials-page.test.tsx`：通过，4 个测试文件、13 条测试。
- `bunx eslint src/components/app/action-feedback.ts src/components/app/action-feedback.test.ts src/components/app/inline-error.tsx src/components/app/inline-error.test.tsx src/app/query-client.ts src/app/query-client.test.ts src/lib/typed-react-query.ts src/features/credentials/credentials-page.tsx src/features/auth/auth-settings-page.tsx`：通过。
- 浏览器验证：`http://localhost:5173/login?reason=logged-out&redirect=/console/credentials` 可加载登录页，无 Vite/React 错误覆盖，toast 容器为左上角。

## 遗留问题

- 当前本地后端返回 5xx，无法在浏览器中完成登录并点击真实凭证页“刷新模型”；凭证刷新成功文案已由单元测试覆盖。
- 纯 query 重新加载、导航、筛选和分页不弹成功 toast，避免浏览行为产生噪音。
