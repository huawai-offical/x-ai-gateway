# REQ-20260522-003 提示气泡化与超大文件组件化拆分

状态：Done  
日期：2026-05-22  
上游来源：用户指令“调试工作台预览或执行失败……告警提示这块，用渐变消失的气泡框弹出就好，在左上角弹出，排查所有的告警和提示；前后端文件存在巨大化趋势，一个文件上千行，需要进行组件化拆分”

## 背景

调试工作台在预览或执行失败时会把 `InlineError` 直接渲染在表格上方，例如“未找到可用的 DistributedKey”。这类错误提示会占据布局位置并干扰表格阅读。当前项目多处使用 `InlineError`，需要统一成左上角渐隐气泡提示。

同时前后端已有多个文件超过 1000 行，前端调试工作台、账号分组详情、凭证页、治理页等文件出现组件职责聚合趋势；后端资源执行器、文件服务、聊天执行服务等文件也明显偏大。需要开始组件化拆分，优先处理本次涉及的调试工作台页面。

## 目标

- 将全局 `InlineError` 呈现改为左上角渐隐气泡提示，不再在页面表格上方占位。
- 排查现有前端提示入口，优先统一 `InlineError` 和 `sonner` 的位置与视觉口径。
- 拆分调试工作台超大页面，把 tab/表单/结果区提取为可复用子组件。
- 产出前后端超大文件清单和后续拆分边界，避免继续堆叠单文件。

## 范围

- `web/src/components/app/inline-error.tsx`
- `web/src/app/providers.tsx`
- `web/src/features/workbench/workbench-page.tsx`
- `web/src/features/workbench/` 下新增组件文件。
- 相关前端测试与本地任务索引。

## 非目标

- 不在本轮重构全部 1000 行以上文件。
- 不改变调试工作台后端 API 契约。
- 不删除通用确认弹窗 `window.confirm`，除非后续单独要求。
- 不把所有成功提示强制改写，只统一当前错误/告警主入口。

## 风险

- `InlineError` 由布局内组件变为 toast 后，依赖错误文本在 DOM 中的测试需要同步调整。
- 调试工作台页面拆分时，需要避免改变请求、预览、执行和 Tab 切换行为。
- 超大后端文件多处属于核心执行链路，拆分需要分任务渐进处理。

## 验收标准

1. 调试工作台预览或执行失败时，错误提示以左上角气泡方式出现，不再插入表格上方。
2. 全局 `InlineError` 不再渲染占位卡片，统一通过渐隐 toast 呈现错误标题、详情和 traceId。
3. `sonner` toast 默认位于左上角。
4. 调试工作台页面拆出子组件，主页面行数下降，职责边界更清晰。
5. 相关前端类型检查和定向测试通过。
6. 文档记录前后端超大文件清单与后续拆分建议。

## 测试边界

- `npm run typecheck`
- `npm test -- --run` 覆盖 `inline-error`、`workbench-page` 和相关页面。
- 必要时使用浏览器验证调试工作台错误提示位置。

## 实现结果

- `web/src/components/app/inline-error.tsx` 已从页面内告警卡片改为 `toast.error`，保留错误标题、详情、`traceId` 和重试动作，但不再渲染 DOM 占位。
- `web/src/components/ui/sonner.tsx` 已将全局 `Toaster` 位置固定为左上角，所有 `sonner` 成功、信息、警告和错误提示统一走同一位置。
- 调试工作台预览或执行失败时，`未找到可用的 DistributedKey` 这类错误会以左上角气泡显示，不再插入表格或结果区上方。
- `web/src/features/workbench/workbench-page.tsx` 已拆出：
  - `web/src/features/workbench/workbench-presets.ts`：调试预设与 `DebugPreset` 类型。
  - `web/src/features/workbench/workbench-components.tsx`：阶段卡片、面板、JSON 区块、追踪时间线、执行结果卡片等展示组件。
- `workbench-page.tsx` 从约 1243 行降到 938 行，主文件聚焦状态管理、请求构造和调试流程拼装。
- 新增 `web/src/components/app/inline-error.test.tsx` 覆盖 toast 化行为和 `traceId` 透传。

## 提示入口排查

- 当前绝大多数失败提示都通过 `InlineError` 进入，已被本轮统一为左上角渐隐 toast。
- `web/src/features/auth/login-page.tsx` 和 `web/src/features/auth/auth-settings-page.tsx` 存在直接 `toast.*` 调用，已受全局左上角 `Toaster` 控制。
- `web/src/features/auth/login-page.tsx` 仍有 `Alert`，主要用于登录挑战/POW 的持久状态展示，不属于本轮“失败告警气泡化”范围；后续可单独判断是否改为普通状态面板。
- 未发现 `window.alert` 或裸 `alert(...)`。
- 发现 18 处 `window.confirm` 原生阻塞确认弹窗，已拆为后续任务 [TASK-20260522-004](../../tasks/done/TASK-20260522-004-confirm-dialog-notification-unification.md)。

## 超大文件清单

| 优先级 | 文件 | 行数 | 拆分方向 |
| --- | --- | ---: | --- |
| P0 | `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java` | 4025 | 拆 responses、vector-store、upload、media、provider adapter、upstream client、cursor |
| P0 | `web/src/features/accounts/account-group-detail-page.tsx` | 2186 | 拆 header/edit、分布式 Key、运行态操作、账号表、凭证表、auth.json 导入向导 |
| P0 | `web/src/features/credentials/credentials-page.tsx` | 1731 | 拆创建向导、批量导入解析、库存表、模型选择器、查询/mutation hooks |
| P0 | `web/src/features/accounts/account-groups-page.tsx` | 1560 | 拆分组列表、创建向导、批量导入、auth.json parser、共享 multiselect |
| P0 | `src/main/java/com/prodigalgal/xaigateway/admin/application/GatewayChatExecutionService.java` | 1219 | 拆 request builder、fallback executor、stream attempt、usage recorder、protocol body writer |
| P1 | `web/src/features/ops/governance-page.tsx` | 1360 | 拆 error rules、route guards、runtime states、simulation、reorder utils |
| P1 | `web/src/features/models/models-page.tsx` | 1182 | 拆 model catalog、alias form wizard、preview panel、payload mapper |
| P1 | `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutionService.java` | 1188 | 拆 OpenAI resource、file/upload execution、route context、metadata mapping |
| P1 | `src/main/java/com/prodigalgal/xaigateway/gateway/core/file/GatewayFileService.java` | 1187 | 拆 local file store、OpenAI style sync、Gemini file sync、Anthropic binding、target resolver |
| P1 | `src/main/java/com/prodigalgal/xaigateway/admin/application/OfficialAccountAdminService.java` | 1134 | 拆 import resolver、quota refresh、Codex smoke、metadata sanitizer |
| P1 | `src/main/java/com/prodigalgal/xaigateway/gateway/core/observability/GatewayRequestLifecycleService.java` | 1109 | 拆 lifecycle recorder、usage snapshot、runtime metric writer、redaction/serialization |
| P1 | `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java` | 1104 | 拆 CRUD、connectivity test、OpenAI smoke、functional provider smoke、classification policy |
| P2 | `web/src/features/workbench/workbench-page.tsx` | 938 | 本轮已拆出预设和展示组件，后续可继续拆 request editor、preview panel、JSON parser/inspect utils |

剩余超大文件分批治理已拆为后续任务 [TASK-20260522-005](../../tasks/backlog/TASK-20260522-005-giant-file-decomposition-roadmap.md)。

## 验证结果

- `npm run typecheck`：通过。
- `npm test -- --run src/components/app/inline-error.test.tsx src/features/workbench/workbench-page.test.tsx src/features/auth/auth-settings-page.test.tsx`：3 个测试文件、4 条测试通过。
- `npx eslint src/components/app/inline-error.tsx src/components/app/inline-error.test.tsx src/components/ui/sonner.tsx src/features/workbench/workbench-page.tsx src/features/workbench/workbench-components.tsx src/features/workbench/workbench-presets.ts`：通过。
- `npm run lint`：未完全通过；阻塞项来自既有文件 `table-pagination.tsx`、`theme-switch.tsx`、`credentials-page.tsx`、`tls-profiles-page.tsx`，本轮新增和修改文件的 lint 已通过。
- 浏览器验证 `http://localhost:5173/login?reason=logged-out&redirect=/console/workbench`：toast 容器实际属性为 `data-x-position="left"`、`data-y-position="top"`，左上角偏移 24px，前端 console error 为空。

## 关联任务

- [TASK-20260522-003](../../tasks/done/TASK-20260522-003-toast-feedback-and-component-splitting.md)
- [TASK-20260522-004](../../tasks/done/TASK-20260522-004-confirm-dialog-notification-unification.md)
- [TASK-20260522-005](../../tasks/backlog/TASK-20260522-005-giant-file-decomposition-roadmap.md)
