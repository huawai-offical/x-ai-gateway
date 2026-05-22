# REQ-20260521-008 系统主题改为默认 Dark，并支持 Dark / Light 双样式

状态：Done  
日期：2026-05-21  
上游来源：用户指令“系统界面的默认样式修改成 dark，做成双样式 dark/light”

## 背景

当前系统界面默认样式仍未明确收敛到以 `dark` 为默认主题的双样式体系。随着现役 UI 汉化与页面收口推进，主题层也需要统一：默认进入 `dark`，同时保留 `dark / light` 两套样式以满足不同场景。

## 目标

- 将系统界面的默认主题切换为 `dark`。
- 建立稳定的 `dark / light` 双样式切换入口。
- 让主题选择在用户刷新后保持一致。
- 尽量通过全局主题 provider、CSS token 和壳层组件完成，不对业务页面做大面积重复改造。

## 范围

- `web/src/app/` 下布局、provider、壳层与共享主题入口。
- `web/src/index.css`、主题 token、全局样式和必要的 shared component 样式。
- Console、Portal、Public、Workbench 的基础背景、文字、边框和卡片层级兼容。

## 非目标

- 本轮不做第三套主题，也不增加品牌换肤系统。
- 本轮不重做每个页面的视觉设计，只做主题系统与兼容性收口。
- 本轮不引入需要后端参与的用户偏好存储；优先使用前端本地持久化。

## 风险

- 如果仅切换默认颜色，不统一 token 和壳层，容易出现局部页面反差失控。
- 如果主题切换没有持久化，刷新后体验会反复跳变。
- 如果暗色默认没有覆盖 Portal/Public 等页面，用户会看到“局部 dark、局部 light”的割裂状态。

## 验收标准

1. 首次进入系统默认使用 `dark` 主题。
2. 系统存在明确可用的 `dark / light` 切换入口。
3. 用户切换后的主题在刷新后保持一致。
4. Console、Portal、Public、Workbench 的基础界面在两种主题下均可用，无明显文字/背景对比问题。
5. 前端 `bun run typecheck` 通过，必要时补充定向测试。

## 测试边界

- 代码检索：主题 provider、主题存储键、全局样式 token
- 前端：`bun run typecheck`
- 前端：与主题切换相关的定向测试
- 手工检查：Console、Portal、Public、Workbench 的主题切换与刷新持久化

## 关联文档

- [REQ-20260521-007](./REQ-20260521-007-ui-chinese-only-localization.md)
- [REQ-20260520-001](./REQ-20260520-001-ui-ux-console-portal-experience.md)

## 实现结果

- 已在 `web/src/app/providers.tsx` 接入 `ThemeProvider`，默认主题切换为 `dark`，并使用 `x-ai-gateway:theme` 做本地持久化。
- 已在共享壳层中提供 `ThemeSwitch` 入口，覆盖桌面与窄屏布局，支持 `dark / light` 双样式切换。
- 已配合共享壳层与运维观测链路页面，将背景、文字、边框、卡片等基础样式收口到主题 token，避免默认深色下出现大面积浅色硬编码。
- 已补齐 `layout` 主题回归测试，验证首次进入默认 `dark`，并校验切换后本地存储持久化。

## 验证结果

- 通过：`bun run typecheck`
- 通过：`bun run vitest run src/app/layout.test.tsx`
- 通过：`bun run vitest run src/app/navigation.test.ts src/app/operations-router.test.tsx src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-page.test.tsx src/features/incidents/incidents-page.test.tsx src/features/request-logs/request-logs-page.test.tsx src/features/accounts/codex-onboarding-page.test.tsx src/features/portal/portal-home-page.test.tsx src/features/traces/traces-page.test.tsx src/features/upstream-cache/upstream-cache-page.test.tsx`

## 遗留事项

- `ops-page` 相关测试在 jsdom 下仍会输出 `recharts` 容器宽高为 0 的 stderr 提示，但测试结果通过，未构成本轮阻塞。
