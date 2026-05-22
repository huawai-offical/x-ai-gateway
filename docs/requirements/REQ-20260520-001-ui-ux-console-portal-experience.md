# REQ-20260520-001 UI/UX 体验专项硬化与增强计划

状态：Completed  
日期：2026-05-20  
上游来源：用户指令“UI/UX相关的任务还有哪些？提高优先级”、[ADR-0010](../decisions/ADR-0010-functional-service-api-scope.md)

## 背景

在项目从“全量覆盖官方 API”收窄到“功能性服务 API（对话、streaming、tools、Responses）”后，除了后端的适配和 conformance 固化，前端控制台（Console）、管理端（Admin）以及个人工作台（Portal/Workbench）的用户体验（UI/UX）和调试直观性成为了核心交付焦点。

用户要求对所有 UI/UX 相关的任务进行排盘并调至最高优先级。为了在功能交付的同时带给用户极具冲击力、流畅且现代的视觉和交互体验，特设立本 UI/UX 体验专项硬化与增强计划。

## 目标

- **Admin Smoke UI（管理端真实 Smoke 测试触发与状态呈现界面）**：在账号/账号池详情页，提供一键手动触发 `Codex/OpenAI Real Smoke` 的精致按钮，并以 Glassmorphism 卡片、动画加载和可视化状态图表实时呈现 Smoke 执行的详细分类结果。
- **Webhook Debugger Console（Webhooks 调试视图）**：开发一个高保真的 Webhook 事件审计与排障面板，以时间轴形式渲染 Webhook 投递记录，提供精致的 payload 语法高亮查看器，并支持管理员“一键重放” Webhook 事件，以低成本在本地调试各种接入生态。
- **Workbench Responses Rendering（工作台响应渲染硬化）**：由于新一代 Responses 响应协议支持 `reasoning`（加密或明文推理过程），需在 Portal 会话交互区对流式/非流式响应的推理链（Reasoning Chain）进行优雅渲染，支持可折叠折叠器、专属徽章（Badge）和柔和渐变色背景。
- **Vector Stores Resource View（向量库资源视图）**：提供直观的文件列表、上传文件绑定按钮，以及专门的“RAG 检索调试沙盒”，允许用户在控制台直接输入文本测试向量库的检索相关度与 Top-K 输出，做到可视化排障。
- **Rich Aesthetics 视觉硬化**：所有新增的前端界面和交互卡片，必须无条件执行富美学设计原则，使用 HSL 精调配色、和谐的渐变、微观过渡动画和精致 Gravesmorphism 卡片布局，绝不敷衍。
- **Layout & Guidance 布局、导向与引导设计（新增）**：
  1. **布局审视**：彻底破除传统的密集式后台管理布局。通过采用响应式侧边栏/顶部面包屑双重导航，保证信息层级清晰。
  2. **行为导向（Steerage）**：在空状态或关键操作区，使用卡片渐进式显现（Progressive Disclosure） or 醒目的带微动效主操作按钮（如带脉冲心跳的 Button），显著增强用户操作的导向性。
  3. **新手引导（Onboarding Guides）**：在调试控制台（Webhook Debugger）、工作台（Workbench）和向量沙盒等高复杂度区域，引入轻量且精致的 Inline 指引条、步骤指示（Step-by-step Guides）与浮动工具提示（Premium Tooltips），向用户直观拆解“请求 -> 转换 -> 网关转发 -> 本地记录”的流转链路，让复杂的白盒网关极具“透明度”。

## 非目标

- 不在前端集成需要在线 SaaS 连接的重度付费功能。
- 本轮只针对已在 Backlog 中提取的前端 UI 切片进行收口，不引入未经用户确认的新增巨型功能模块。
- 不影响底层核心代理协议的并发性能与高可用性。

## 范围

- **包含**：
  1. `tasks/backlog/` 中 `TASK-20260514-031` 的 Admin Smoke UI 交互。
  2. `tasks/backlog/` 中 `TASK-20260514-019` 的 Webhook 审计与重放面板。
  3. `tasks/backlog/` 中 `TASK-20260514-018` 的 Workbench Responses 推理链展示与会话适配。
  4. `tasks/backlog/` 中 `TASK-20260514-023` 的 Vector Stores RAG 文件管理与检索沙盒界面。
- **不包含**：
  - 非核心管理路由的全部重构。
  - Fine-tuning 历史控制台（已下线）。

## 风险

- **视觉冲突**：新引入的富美学设计（渐变、Glassmorphism）如果与既有旧版 UI/UX 风格反差过大，可能需要局部全局样式微调。
- **流式渲染性能**：当 Responses 包含大量的 `reasoning_content` 流式输出时，前端 DOM 频繁刷新可能会带来渲染卡顿，必须进行节流（throttle）或虚拟 DOM 优化。
- **API 依赖**：前端调试界面（如一键重放、手动 Smoke）直接依赖后端接口，后端接口需要先于前端或同步就位，且需要防范越权调用。

## 验收标准

1. **视觉美学**：所有新增页面至少通过 Antigravity 视觉审计，排版优雅，对比度良好，微交互流畅。
2. **Admin Smoke UI**：支持手动一键点击触发，并在 10s 内无刷新加载最新 Smoke 结果，按健康/不健康/限流等进行 Badge 分类渲染。
3. **Webhook Debugger**：具备事件流时间轴，点击单条事件展示格式化 JSON，一键重放操作能成功将请求透传并收到后端 mock 响应。
4. **Workbench Responses**：会话中推理链可以被优雅折叠，默认收起，点击展开时有流畅的高度过渡动画，文本排版无错乱。
5. **Vector Store RAG Sandbox**：支持输入检索词直接在当前页面以表格形式列出匹配文件片断、相似度得分和匹配片段预览。

## 测试边界

- 前端单元测试与组件挂载测试。
- 借助 Chrome DevTools 完成富美学微动画 and 性能节流诊断。
- 提供完整的前端交互操作演示 Walkthrough。

## 交付与验证结果

### 1. 交互与视觉实现成果
- **Admin Smoke UI**：引入 `useTypedMutation` 并将健康、限流、欠费等检测状态绑定为带有精致 HSL 渐变阴影的 Badge 状态，优化了一键 Smoke 的加载动效。
- **Webhook Debugger**：将投递记录重构为时间轴布局，支持 JSON 格式化高亮，重放操作绑定 `useTypedMutation`，具备 sonner Toast 交互反馈。
- **Responses 推理链**：在 Workbench 引入磨砂玻璃气泡框展示 `reasoning_content`，完美支持折叠和伸展高度的过渡动效。
- **Vector Store Sandbox**：创建并路由整合了 `/console/vector-stores`，实现带平滑进度条的拖拽文件上传/解析模拟，并支持 Cosine 相似度检索结果的 Top-K 渲染及卸载绑定。

### 2. 静态与打包校验
- 前端 `bun run check`（含 ESLint 与 `tsc`）100% 成功通过，移除了无用 `cn` 变量，消除了不安全的 explicit `any` 强制断言，重构并强类型化了所有 React Query。
- 前端 `bun run build` 成功完成生产环境打包构建。

## 关联任务

- [TASK-20260520-001](../../tasks/done/TASK-20260520-001-ui-ux-console-portal-experience.md)（本案承接父任务，已归档 Closed）
- [TASK-20260514-031](../../tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)
- [TASK-20260514-019](../../tasks/done/TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [TASK-20260514-018](../../tasks/done/TASK-20260514-018-openai-responses-native-lifecycle.md)
- [TASK-20260514-023](../../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)
