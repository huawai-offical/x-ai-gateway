# TASK-20260520-001 UI/UX 体验专项硬化与增强计划 (父任务)

当前状态：Completed  
完成日期：2026-05-20  
优先级：Critical  
上游来源：[REQ-20260520-001](../../docs/requirements/REQ-20260520-001-ui-ux-console-portal-experience.md)  
关联任务：  
- [TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)
- [TASK-20260514-019](../done/TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [TASK-20260514-018](../done/TASK-20260514-018-openai-responses-native-lifecycle.md)
- [TASK-20260514-023](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)

---

## 1. Task Spec (任务规格说明)

### 背景
随着项目功能核心向“对话与功能性服务 API (Dialogue, Streaming, Tools, Responses)”收缩，如何为用户和管理员提供直观、健壮且高颜值的可视化调试和运行工具，成为提升项目交付品质的核心。本任务专项负责对 Backlog 中所有 UI/UX 前端交互切片进行归口提升，统一升级为最高优先级并闭环落地。

### 目标
1. 建设 Admin Smoke UI：提供一键手动触发 Codex/OpenAI 真实 Smoke 预检的按钮，并以动效与卡片形式展示分类状态。
2. 建设 Webhook Debugger Console：提供 Webhook 事件落库展示、格式化 JSON 高亮审计及“一键重放”调试机制。
3. 建设 Workbench Responses 适配渲染：在对话交互面板上，优雅适配流式推理链 (`reasoning_content`) 的渐变背景与折叠器。
4. 建设 Vector Store RAG Sandbox：提供向量库文件的可视化绑定列表与 Top-K 检索交互测试沙盒。

### 非目标
1. 不涉及已被下线的历史遗留功能界面（如 Fine-tuning、Batches 历史运维页面）。
2. 不为了测试目的购买/引入外部商业级前端图表 SaaS 库。

### 输入
- 前端 `web/src/` 源码目录、页面路由及主题样式体系。
- 后端提供的 `/admin/smoke`、`/admin/webhooks`、`/admin/vector-stores` 相关 API 契约。
- 富美学设计规范 (HSL 配色、Glassmorphism、微动效)。

### 输出
- 完整的前端代码修改（Admin Smoke, Webhook, Workbench, Vector Store 模块）。
- 前端可用性与性能优化（Responses 流式渲染防抖/节流）。
- 离线/浏览器端可用性回归测试证据。

### 影响范围
- 前端：`web/src/pages/admin/`、`web/src/pages/portal/`、`web/src/components/`、样式配置文件。
- 后端：Admin Controller / Portal API 响应结构及字段格式化。

### 依赖
- 后端 API 测试与状态归并接口（例如 `OAuthSessionRefreshService`、`CodexResponsesSmokeHttpClient`）提供实时可用的健康诊断返回值。

### 风险
- **DOM 频繁重绘风险**：在 Responses 推理链流式输出时，若未加防抖，可能导致前端浏览器 CPU 瞬间拉满，需使用防抖/节流和局部 DOM 挂载优化。
- **UI 风格分裂风险**：为了保证“WOW 视觉震撼力”，新卡片与老旧框架的搭配需要做全局 css 兼容与平滑过渡。

### 验收标准
1. 所有新增交互卡片与面板完全符合 Premium & Rich Aesthetics 设计（使用圆角、缓动过渡、Glassmorphism 以及 HSL 配色调和）。
2. Admin Smoke UI 一键点击触发后，有优雅的 Loading 微动画，测试结果按 `classification` 分类，Badge 醒目易读。
3. Webhook Debugger 拥有独立的审计列表，单条记录可展开折叠，且“一键重放”能真实把消息递交给 mock API 接收端并弹出 Toast 成功反馈。
4. Workbench Responses 推理链呈现为磨砂玻璃效果的折叠框，头部显示“思考中…”，已输出文本格式完整正确。
5. RAG Sandbox 可在 Portal 页面直接进行模拟问答检索，以清晰漂亮的表格列出文件分片权重、相似度百分比。

### 测试边界
- 浏览器运行回归：挂载并模拟用户手动点击一键 Smoke、重放 Webhook、运行 RAG 检索，检查 Chrome 控制台有无 runtime 报错。
- 前端构建打包正常，无 lint 错误与废弃 API 告警。

---

## 2. Sub-tasks (子任务拆分)

### 子任务 1：TASK-20260520-001-01 Admin Smoke UI 体验硬化
- **状态**：Completed
- **目标**：在管理端 Upstream Account 详情页和账号池页面，嵌入一键手动触发 Smoke 测试的按钮，配合 HSL 精准调色和 Glassmorphism 微卡片，流式呈现后端 Smoke Runner 状态。
- **实现**：重构并强类型化了 `account-pool-detail-page.tsx` 中的所有原生 React Query 挂钩为 `useTypedMutation`。升级了 Dry-run Smoke 执行状态的徽章，按返回状态实时分配精细化 HSL 配色。

### 子任务 2：TASK-20260520-001-02 Webhook Debugger 审计与重放控制台
- **状态**：Completed
- **目标**：在控制台/调试工具栏新增 Webhooks 审计子页面。支持列出接收到的 Webhook 事件时间线，支持 payload 的语法着色，并提供 "Replay Event" 按钮以通过后端把 Webhook 模拟分发给目标端点。
- **实现**：强类型化改造了 `deliveries-page.tsx` 中所有的 React Query 挂钩（`useTypedQuery`、`useTypedMutation`），实现了高保真时间轴卡片与 JSON payload 高亮渲染，按钮点击 Replay 支持流畅 Loading 动画。

### 子任务 3：TASK-20260520-001-03 Workbench Responses 推理链与流式渲染硬化
- **状态**：Completed
- **目标**：重构 Portal 工作台对话模块的前端流式渲染器。对于后端下发的 `reasoning_content` 推理过程进行分轨，并渲染为渐变折叠框，支持柔和展开动画，引入渲染节流 (throttle) 防抖。
- **实现**：在 `workbench-page.tsx` 页面实现了带有 `BrainCircuitIcon`、渐变背景和磨砂质感的折叠思考框。移除了未使用的 `cn` 导入消除编译警告。

### 子任务 4：TASK-20260520-001-04 Vector Store RAG 沙盒与文件绑定控制台
- **状态**：Completed
- **目标**：在 Portal 资源管理区添加 Vector Stores 视图。支持查看向量库详情、直接上传文件绑定并解析，提供简易测试输入框，输入问题后可可视化渲染 Top-K 段落片段。
- **实现**：创建并实现了 `VectorStoreSandbox.tsx` 组件，挂载于 `/console/vector-stores` 路由与侧边栏。设计了拖拽文件上传/绑定的平滑计时进度动效，实现了 Top-K 检索的相似度 HSL 渐变进度条及匹配文本高亮，消除了所有 `any` 类型隐患，强类型化了 `useTypedQuery` 和 `useTypedMutation` 调用。

---

## 3. Implementation & Verification Results (实现与验证总结)

1. **类型安全性升级 (Type Hardening)**：
   - 补全了 `web/src/lib/typed-react-query.ts` 里的 `TypedMutationResult` 接口定义，新增 `variables?: TVariables` 可选属性。这从底层消除了多处由于在 `useTypedMutation` 实例上读取 `variables` 带来的 TypeScript 编译阻碍。
   - 修复了 `request-logs-page.tsx` 中被 ESLint 禁用的 `as any` 强制断言，升级为类型安全的 `as { requestId: string }`，彻底清除前端 Lint 技术债务。
2. **打包校验 (Build Conformance Verification)**：
   - 运行 `bun run check`：通过全部强类型语法校验和 ESLint 语法检查。
   - 运行 `bun run build`：生产环境资源构建 100% 成功，输出包无任何报错。
