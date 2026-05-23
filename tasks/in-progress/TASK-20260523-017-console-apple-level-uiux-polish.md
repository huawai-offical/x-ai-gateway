# TASK-20260523-017 控制台 Apple-level UI/UX 精修

## 状态

In Progress

## 背景

用户在账号分组详情页截图中指出左侧导航与右侧内容区域的顶部对齐不够精细，并要求排查其他页面 UI/UX 是否能对标 Apple 的设计精细程度。本任务承接 `REQ-20260523-012`，先处理全局 App Shell 与第一屏观感，再抽查高频页面。

## 目标

- 修复 App Shell 侧栏滚动条与右侧标题栏之间的视觉断层。
- 统一侧栏品牌区与右侧 header 的高度节奏。
- 降低全局控制台滚动面、边框和按钮噪声。
- 抽查账号分组详情、厂商目录、上游凭证、账号分组列表四类高频页面。

## 非目标

- 不修改后端 API、数据模型、账号分组路由或厂商能力逻辑。
- 不做完整视觉重构，不引入新的设计依赖。
- 不把所有历史页面一次性全部重排；无法在本切片完成的问题需记录为后续 UI/UX backlog。

## 上游来源

- [REQ-20260523-012 控制台 Apple-level UI/UX 精修](../../docs/requirements/REQ-20260523-012-console-apple-level-uiux-polish.md)
- 用户截图反馈：账号分组详情页左侧导航与右侧内容区顶部对齐问题。

## 输入

- 用户提供的账号分组详情页截图。
- 现有 App Shell、PageSection 和高频页面样式实现。

## 输出

- App Shell 对齐与滚动条样式修复。
- 高频页面视觉抽查结果。
- 前端 typecheck、必要测试和浏览器视觉验证记录。
- 任务状态回写与后续建议。

## 影响范围

- `web/src/components/app/app-shell.tsx`
- `web/src/index.css`
- 可能触及：`web/src/components/app/page-section.tsx`
- 视觉抽查：账号分组详情、厂商目录、上游凭证、账号分组列表

## 依赖

- 本地前端 dev server 或可启动的 Vite 开发环境。
- 后端服务或现有浏览器会话用于真实页面渲染。

## 风险

- 侧栏品牌区高度绑定 header 高度后，极窄屏或多行 breadcrumb 可能改变抽屉节奏。
- 滚动条低干扰样式需要兼顾 Chrome/WebKit 与 Firefox。
- 如果后端服务不可用，浏览器只能验证 shell 和错误态，无法完整验证真实数据态。

## 验收标准

- 截图标注区域的突兀竖向滚动条消失或降到低干扰状态。
- 侧栏品牌区底线与右侧 header 底线在桌面宽度对齐。
- 桌面和移动宽度下，控制台 header、侧栏、主内容不重叠。
- `bun run typecheck` 通过。
- 若修改影响已有测试页面，运行对应 Vitest 测试。

## 测试边界

- 自动验证：`cd web; bun run typecheck`
- 定向测试：涉及账号分组或厂商目录页面时运行对应测试文件。
- 手工/浏览器验证：账号分组详情页桌面宽度、移动宽度；厂商目录、上游凭证、账号分组列表抽查。

## 关联文档

- [REQ-20260523-012 控制台 Apple-level UI/UX 精修](../../docs/requirements/REQ-20260523-012-console-apple-level-uiux-polish.md)

## 关联任务

- [TASK-20260523-013 厂商目录 UI 收敛为 Vendor -> Endpoint -> Group -> Credential](TASK-20260523-013-provider-catalog-vendor-endpoint-group-credential-ui.md)
- [TASK-20260521-012 控制台健康评分、Codex 模型、TLS 画像与调试台体验修正](../done/TASK-20260521-012-console-health-codex-model-tls-workbench-ux.md)

## 当前进度

- 2026-05-23：已确认首要问题位于 App Shell 侧栏滚动面和 header 高度节奏。

