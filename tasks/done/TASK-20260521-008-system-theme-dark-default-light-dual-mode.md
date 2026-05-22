# TASK-20260521-008 系统主题默认 Dark 与双样式切换

状态：已完成  
优先级：High  
上游来源：[REQ-20260521-008](../../docs/requirements/REQ-20260521-008-system-theme-dark-default-light-dual-mode.md)

## 任务类型

父任务

## 背景

用户要求把系统界面的默认样式改为 `dark`，并同时具备 `dark / light` 双样式能力。本任务负责主题 provider、全局样式与共享壳层的接入和收口。

## 目标

- 默认主题设为 `dark`
- 支持 `dark / light` 切换
- 主题选择本地持久化
- 保持现役 UI 页面在两套主题下可用

## 非目标

- 不增加第三套主题
- 不重构业务页面信息架构
- 不接入后端用户偏好接口

## 输入

- `web/src/app/`
- `web/src/index.css`
- 共享 UI 组件和页面壳层

## 输出

- 默认 dark 的主题系统
- dark/light 切换入口
- 相关文档与测试回写

## 影响范围

- 全局主题 token
- 页面壳层和主题入口
- 现役页面的基础背景、边框、文字与卡片表现

## 依赖

- 当前前端样式体系
- 共享 provider 与布局结构

## 风险

- 主题切换如果直接硬改 class，容易影响既有页面局部样式。
- 若存储键或 hydration 时序处理不好，会出现闪烁或主题回跳。

## 验收标准

- [x] 首次进入默认 dark
- [x] 支持 dark/light 切换
- [x] 刷新后保持上次主题
- [x] 现役界面两套主题下基础可用
- [x] `bun run typecheck` 通过

## 测试边界

- 代码检索：主题 provider、样式 token、切换入口
- 前端：`bun run typecheck`
- 前端：定向测试

## 当前状态

已完成

## 实现结果

- 已在 `web/src/app/providers.tsx` 接入 `ThemeProvider`，默认主题设为 `dark`，并关闭 system 跟随，避免首次进入时主题漂移。
- 已在共享壳层 `AppShell` 接入 `ThemeSwitch`，覆盖桌面和移动端入口，支持 `dark / light` 切换。
- 已通过主题 token 改造共享壳层与运维观测链路页面的基础色面，保证深浅主题切换下的背景、文字、边框与卡片层级可用。
- 已补齐 `layout` 相关测试，验证首次进入默认深色和切换后的本地持久化行为。

## 验证结果

- 通过：`bun run typecheck`
- 通过：`bun run vitest run src/app/layout.test.tsx`
- 通过：`bun run vitest run src/app/navigation.test.ts src/app/operations-router.test.tsx src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-page.test.tsx src/features/incidents/incidents-page.test.tsx src/features/request-logs/request-logs-page.test.tsx src/features/accounts/codex-onboarding-page.test.tsx src/features/portal/portal-home-page.test.tsx src/features/traces/traces-page.test.tsx src/features/upstream-cache/upstream-cache-page.test.tsx`
