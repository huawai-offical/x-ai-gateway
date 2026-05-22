# TASK-20260522-004 原生确认弹窗与通知入口统一

状态：Backlog  
优先级：High  
上游来源：[REQ-20260522-003](../../docs/requirements/REQ-20260522-003-toast-feedback-and-component-splitting.md)

## 任务类型

子任务

## 背景

本轮已将 `InlineError` 统一为左上角 toast，并确认未发现 `window.alert` 或裸 `alert(...)`。但控制台仍有 18 处 `window.confirm` 原生阻塞确认弹窗，风格与现有深色控制台不一致，也不便统一交互和测试。

## 目标

- 新增或复用统一的 `ConfirmDialog` / `useConfirm`。
- 将删除类 `window.confirm` 迁移为项目内确认弹窗。
- 确认后的成功、失败和取消反馈继续走 `sonner`。
- 保持删除操作语义不变。

## 非目标

- 不改变删除 API。
- 不新增批量删除能力。
- 不把持久状态提示强制改为 toast。

## 输入

- `web/src/features/accounts/account-group-detail-page.tsx`
- `web/src/features/credentials/credentials-page.tsx`
- `web/src/features/keys/keys-page.tsx`
- `web/src/features/keys/key-detail-page.tsx`
- `web/src/features/ops/governance-page.tsx`
- `web/src/features/ops/ops-alerts-page.tsx`
- `web/src/features/models/models-page.tsx`
- `web/src/features/integrations/*.tsx`
- `web/src/features/network/*.tsx`
- `web/src/features/operations/windows-page.tsx`
- `web/src/features/user-domain/*.tsx`

## 输出

- 统一确认弹窗组件或 hook。
- 删除确认迁移清单。
- 对应页面的定向测试。

## 影响范围

删除确认交互、表格行操作、详情页删除操作、相关测试。

## 依赖

- 全局 `Toaster` 已统一左上角。
- 现有按钮和弹窗组件体系。

## 风险

- 确认弹窗迁移容易遗漏异步 pending 状态。
- 大页面中删除逻辑和详情抽屉状态耦合，需要分批处理。

## 验收标准

- [ ] 项目内不再有业务删除场景直接调用 `window.confirm`。
- [ ] 确认弹窗支持深色/浅色主题。
- [ ] 删除成功、失败反馈位置统一。
- [ ] 定向测试覆盖至少账号分组、凭证、模型、网络代理、治理页面。

## 测试边界

- `npm run typecheck`
- `npm test -- --run` 覆盖迁移页面。
- 必要时浏览器验证一个删除确认弹窗。

## 当前状态

待处理。
