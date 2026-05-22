# TASK-20260522-003 提示气泡化与超大文件组件化拆分

状态：Done  
优先级：Critical  
上游来源：[REQ-20260522-003](../../docs/requirements/REQ-20260522-003-toast-feedback-and-component-splitting.md)

## 任务类型

父任务

## 背景

调试工作台错误提示当前以页面内 `InlineError` 呈现，容易压在列表或表格上方。前后端多个文件超过 1000 行，维护风险上升，需要从当前问题面开始组件化拆分。

## 目标

- 将 `InlineError` 统一改为左上角 toast，不占页面布局。
- 让调试工作台预览/执行失败提示通过气泡框渐隐消失。
- 拆分 `workbench-page.tsx` 中的高内聚 UI 区块。
- 形成超大文件清单和后续拆分建议。

## 非目标

- 不一次性重构所有超大文件。
- 不改后端调试工作台 API。
- 不改变表格数据、预览结果、执行结果的业务语义。

## 输入

- `web/src/components/app/inline-error.tsx`
- `web/src/app/providers.tsx`
- `web/src/features/workbench/workbench-page.tsx`
- `web/src/features/workbench/types.ts`
- `web/src/features/workbench/utils.ts`

## 输出

- 左上角渐隐 toast 错误提示。
- 组件化后的调试工作台代码。
- 超大文件清单和本地文档回写。
- 定向测试和类型检查结果。

## 影响范围

所有使用 `InlineError` 的前端页面、调试工作台、全局 toast 呈现位置和相关测试。

## 依赖

- `sonner` 已在项目中使用。
- `AppProviders` 已集中挂载 `Toaster`。

## 风险

- 全局改 `InlineError` 会改变多个页面的错误 DOM 呈现。
- 调试工作台较大，拆分需要保持 props 边界清晰。
- 浏览器验证可能依赖本地登录态和后端数据。

## 验收标准

- [x] `InlineError` 使用 toast 呈现错误，不再渲染卡片占位。
- [x] toast 位于左上角并自动消失。
- [x] 调试工作台拆出至少 2 个子组件，主文件行数下降。
- [x] 超大文件清单写入需求或任务。
- [x] 前端类型检查和定向测试通过。

## 测试边界

- `npm run typecheck`
- `npm test -- --run src/components/app/inline-error.test.tsx src/features/workbench/workbench-page.test.tsx`
- 必要时补充浏览器验证。

## 当前状态

已完成，待归档到 `tasks/done/`。

## 实现结果

- `InlineError` 已统一改为 `toast.error`，不再输出页面内告警卡片。
- `Toaster` 已统一到左上角。
- 调试工作台错误提示不再挤占表格/结果区布局。
- `workbench-page.tsx` 已拆出 `workbench-presets.ts` 和 `workbench-components.tsx`，主文件从约 1243 行降到 938 行。
- 已新增 `inline-error` 单元测试，覆盖 DistributedKey 类错误和 `traceId` 展示。
- 已把提示入口、`window.confirm` 遗留点和超大文件清单回写到需求文档。
- 遗留增强已拆入：
  - [TASK-20260522-004](../backlog/TASK-20260522-004-confirm-dialog-notification-unification.md)
  - [TASK-20260522-005](../backlog/TASK-20260522-005-giant-file-decomposition-roadmap.md)

## 验证结果

- `npm run typecheck`：通过。
- `npm test -- --run src/components/app/inline-error.test.tsx src/features/workbench/workbench-page.test.tsx src/features/auth/auth-settings-page.test.tsx`：通过，3 个测试文件、4 条测试。
- `npx eslint src/components/app/inline-error.tsx src/components/app/inline-error.test.tsx src/components/ui/sonner.tsx src/features/workbench/workbench-page.tsx src/features/workbench/workbench-components.tsx src/features/workbench/workbench-presets.ts`：通过。
- `npm run lint`：未完全通过；剩余错误为既有存量问题，位于 `table-pagination.tsx`、`theme-switch.tsx`、`credentials-page.tsx`、`tls-profiles-page.tsx`。
- 浏览器验证：登录页触发 toast 后，`data-x-position="left"`、`data-y-position="top"`，左上角偏移 24px，console error 为空。
