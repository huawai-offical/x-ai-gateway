# TASK-20260507-007 前端可用性验收、表单友好性与移动端体验硬化

状态：Done  
优先级：Medium  
排期：P3-13  
来源：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)
本批需求：[REQ-20260507-007 第五批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-007-next3-observability-ux-preview-closure.md)

## 背景

当前前端已经大量使用 shadcn 风格组件、React Query、页面骨架和状态徽章，但多个页面仍存在 CSV 输入、裸数字 ID、长表格、长表单、空态下一步不足、移动端表格溢出等可用性风险。UI/UX 差距不是单纯“变漂亮”，而是让用户少填错、少迷路、能理解失败原因。

## 目标

- 建立前端可用性验收清单和 Playwright/Vitest 回归。
- 将高风险表单从裸 input/CSV/ID 改为 picker、multi-select、stepper、combobox 或带校验的 field array。
- 强化空态、错误态、加载态和移动端布局。
- 优先覆盖 Codex 接入链路、账号池、Key、request logs、dashboard。

## 详细设计

- 梳理高风险输入：providerType、allowedModels、allowedClientFamilies、proxyId、tlsFingerprintProfileId、distributedKeyId、clientInstanceId。
- 为这些字段建立复用 picker 组件，优先复用现有 API 列表，而不是要求用户输入裸 ID。
- 增加 `web/src/test` 可用性 helpers：viewport matrix、表格溢出检测、按钮文字溢出检测、空态 CTA 检查。
- 将 request logs 等宽表在移动端改成 list/card 或横向滚动带固定操作列。
- 对关键 destructive 操作增加确认语义和禁用条件说明。

## 本批落地范围

- 更新 UX 验收矩阵，补齐 Codex 接入、Codex 观测、账号池 Runtime 三个关键页面。
- 将账号池绑定分布式 Key 从裸 ID 输入改为 picker。
- 对 request logs 和账号池 Runtime 宽表增加横向滚动与稳定最小宽度。
- 空态提供清空筛选或下一步动作。

## 验收标准

- Codex 接入链路高风险字段不再要求用户手写裸 ID。
- 关键页面在桌面和移动 viewport 下无明显文字溢出或操作遮挡。
- 空态包含下一步 CTA 或明确说明。
- 前端测试覆盖至少 5 个核心页面的加载、空态、错误态和移动端布局。

## 风险

- 表单组件化不能引入大型 UI 重构，避免和现有页面大量冲突。
- 移动端优化应以可操作为目标，不牺牲桌面高密度扫描效率。

## 实施记录

- `ux-acceptance` 新增 `mobile-table-overflow` 规则。
- Codex onboarding 路由验收修正为 `/console/accounts/connect/codex`。
- 新增 Codex observability 和 account pool runtime 两个关键 UX 验收页面。
- 账号池绑定分布式 Key 从裸 ID 输入改为兼容性过滤 picker。
- request logs、Codex 观测台、账号池 Runtime 与成员表增加横向滚动和稳定最小宽度。

## 验证记录

- `bun run test -- src/app/ux-acceptance.test.ts src/features/accounts/account-pool-detail-page.test.tsx src/features/request-logs/request-logs-page.test.tsx`：通过。
- `bun run typecheck`：通过。
- `bun run build`：通过。
