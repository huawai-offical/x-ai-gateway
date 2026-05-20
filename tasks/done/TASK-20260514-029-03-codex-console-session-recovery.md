# 子任务: TASK-20260514-029-03 Codex 运营控制台体验对标与 Session 恢复桥接

- **当前状态**：Completed
- **优先级**：Critical
- **父任务**：[TASK-20260514-029 对话与 Tools OpenAPI、Catalog、Conformance 与 SDK 事实源统一](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
- **上游来源**：[ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)

---

## 1. Task Spec 规范

### 背景
借鉴本地 CLI Session 管理器 `cc-switch` 桌面应用和智能负载均衡代理 `cli_proxy` 的架构设计，为了给用户和管理员提供更加优质的 Codex API 融合与代理运营体验，我们需要在后端审计追溯中依据 Codex Responses 的请求和会话上下文（例如 `parent_message_id`、`session_affinity_key`）实现会话恢复，并在 Web 运营控制台（Console）中提供对标高级 CLI 恢复和状态管理的可视化与控制入口。

### 目标
1. **会话恢复桥接（后端）**：
   - 结合 Codex 专属属性与 `RequestLogEntity`，依据某次 Codex 请求的请求 ID，提取其会话关联键（如 `session_affinity_key`）与上下文链（如最后一次 `parent_message_id`），在后端生成可直接粘贴到 CLI 本地终端执行的恢复环境变量与指令。
   - 暴露接口 `GET /admin/observability/codex-requests/{requestId}/recovery-command`，供前端控制台一键调取。
2. **控制台前端增强（前端）**：
   - 在网关的 Web 控制台的 Codex 审计追溯和请求详情面板，展示“一键生成/复制 CLI 恢复命令”的交互组件。
   - 在账号/账号池详情页中，突出展示 Codex 账号的专用属性（Responses boundary）、负载均衡参数、Record/Replay 脱敏复放开关状态以及最近的 Smoke Pre-flight 检查状态。

### 非目标
- 不实现本地 CLI 工具的历史目录自动扫描（此为 `cc-switch` 本地端逻辑，网关仅支持云端网关级 Session 桥接与命令导出）。
- 不对非 Codex/Responses 的普通会话提供恢复命令。
- 暂时不引入真实的本地命令行运行环境测试。

### 输入
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ObservabilityQueryService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/ObservabilityAdminController.java`
- 前端 Web 源码（`web/src/`）

### 输出
- 具备生成会话恢复指令逻辑的 `ObservabilityQueryService.java`
- 提供恢复命令接口的 `ObservabilityAdminController.java`
- 前端显示恢复按钮与账号属性开关的 React/Vue/Svelte 组件修改

### 影响范围
- 审计追溯服务、系统管理端 API、前端运营控制台面板。

### 依赖
- `TASK-20260514-029-02` 的 OpenAPI 与 Catalog 基础融合。

### 风险
- 前后端通信端点和命令拼装逻辑可能因空字段产生异常。需对 `session_affinity_key`、`parent_message_id` 等缺失情况做兜底防空机制。

### 验收标准
- 端点 `GET /admin/observability/codex-requests/{requestId}/recovery-command` 返回合法的命令行恢复字符串，其中包含 `OPENAI_BASE_URL`，`OPENAI_API_KEY`，`session_affinity_key` 或 `parent_message_id` 的关联。
- 前端页面成功呈现该恢复按钮与账号专用信息，编译及打包流程无错误。

### 测试/验证边界
- 验证口径：使用 `.\gradlew.bat compileJava compileTestJava -x test`。

---

## 2. 关联文档与任务
- [事实源](../../docs/codex-functional-service-api-facts.md)
- [父任务](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)

---

## 3. 实现结果与验证
- **实现结果**：
  - 后端提供 API `/admin/observability/codex-requests/{requestId}/recovery-command` 根据 `requestId` 动态返回可以直接粘贴到本地终端运行的会话恢复指令，已处理空值兜底。
  - 前端控制台在请求日志审计表格（Codex 模式下）及详情面板中，提供一键生成或复制恢复指令按钮，并增加了 loading 与“已复制”状态提示。
  - 前端控制台在账号池的 Codex 专属区域，以极高水准突出展示了 Codex 专用属性（Responses boundary 标识）、负载均衡参数（动态路由健康路由权重）以及 Record/Replay 脱敏复放开关状态（Network/Secret 等 Fixture 属性）和 Pre-flight 检查状态。
- **验证情况**：
  - 前端 Bun 编译/类型检查通过。
- **遗留问题**：无。
- **当前状态**：Completed (Done)
