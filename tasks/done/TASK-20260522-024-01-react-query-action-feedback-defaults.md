# TASK-20260522-024-01 React Query 操作默认结果提示

状态：Done  
优先级：Critical  
来源：TASK-20260522-024  
上游来源：[REQ-20260522-023](../../docs/requirements/REQ-20260522-023-action-feedback-toast-coverage.md)

## 任务类型

子任务

## 背景

前端 mutation 操作分散在凭证、账号分组、厂商入口、用户域、运维等页面。逐页补 `toast.success` 容易遗漏，也无法保证后续新增操作遵守“成功/失败都提示”的规则。

## 目标

- 在应用级 `QueryClient` 中统一处理 mutation 成功和失败提示。
- 提供 typed mutation `meta` 类型，让页面能定制操作名和提示文案。
- 让 `InlineError` 对已全局提示的 mutation 错误不重复弹窗。
- 为凭证页刷新模型补充明确成功文案。

## 非目标

- 不修改查询成功提示。
- 不清理 `window.confirm`。
- 不拆分凭证页大文件。

## 输入

- `web/src/app/query-client.ts`
- `web/src/lib/typed-react-query.ts`
- `web/src/components/app/inline-error.tsx`
- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/auth/auth-settings-page.tsx`

## 输出

- 新增 action feedback 工具。
- 修改应用 query client、typed mutation 类型、InlineError 去重逻辑。
- 凭证刷新模型成功提示文案。
- 单元测试覆盖默认成功、默认失败、失败去重和凭证刷新模型定制提示。

## 影响范围

所有应用级 React Query mutation；凭证页刷新模型；使用 `InlineError` 的错误展示入口。

## 依赖

- `sonner` toast。
- `ApiError` 的 `traceId` 字段。
- React Query mutation cache 回调。

## 风险

- 如果某些 mutation 是后台自动触发，会出现默认成功提示；当前检索到的 mutation 多为按钮或表单驱动，风险可接受。
- 现有手写成功 toast 可能重复，需要对已知页面做局部 suppression 或迁移到 `meta.successMessage`。

## 验收标准

- [x] `refreshCredentialMutation` 成功 toast 包含“模型刷新完成”和模型数量。
- [x] 应用 query client 对普通 mutation 成功显示默认成功 toast。
- [x] 应用 query client 对普通 mutation 失败显示失败 toast，并记录错误详情。
- [x] `InlineError` 遇到已全局提示过的 mutation 错误不会重复 toast。
- [x] 定向测试和类型检查通过。

## 测试边界

- `bun run typecheck`
- `bun run test -- --run src/components/app/action-feedback.test.ts src/components/app/inline-error.test.tsx src/app/query-client.test.ts src/features/credentials/credentials-page.test.tsx`
- 变更文件定向 eslint。

## 关联文档

- [REQ-20260522-023](../../docs/requirements/REQ-20260522-023-action-feedback-toast-coverage.md)

## 关联任务

- [TASK-20260522-024](TASK-20260522-024-action-feedback-toast-coverage-parent.md)

## 当前状态

已完成，待归档到 `tasks/done/`。

## 实现记录

- `action-feedback.ts` 提供 `showActionSuccessToast`、`showActionErrorToast`、`markActionErrorToastShown`、`hasActionErrorToastShown`。
- 应用 `queryClient` 的 `MutationCache` 已调用 action feedback。
- `InlineError` 已跳过已全局提示的 mutation 错误。
- 凭证页刷新模型已通过 mutation `meta` 定制成功文案。
- 控制台凭证更新页已抑制全局成功 toast，保留原有“正在要求重新登录”的特定提示。

## 测试/验证

- `action-feedback.test.ts` 覆盖默认成功、定制成功、失败详情和 suppress 行为。
- `query-client.test.ts` 覆盖应用级 `MutationCache` 成功/失败回调。
- `inline-error.test.tsx` 覆盖失败去重。
- `credentials-page.test.tsx` 继续通过，确保凭证页创建、批量创建和 auth.json 导入不回归。

## 遗留问题

- 浏览器真实点击凭证页刷新模型受本地后端 5xx 限制未执行；已由单元测试覆盖前端文案和全局机制。
