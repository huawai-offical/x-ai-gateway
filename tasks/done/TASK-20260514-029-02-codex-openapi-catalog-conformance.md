# 子任务: TASK-20260514-029-02 Codex OpenAPI, Catalog & Conformance 深度融合

- **当前状态**：Completed
- **优先级**：Critical
- **父任务**：[TASK-20260514-029 对话与 Tools OpenAPI、Catalog、Conformance 与 SDK 事实源统一](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
- **上游来源**：[TASK-20260519-002-01](../../docs/codex-functional-service-api-facts.md)

---

## 1. Task Spec 规范

### 背景
根据 x-ai-gateway 功能性服务 API（对话/streaming/tools）的设计边界以及 [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md) 的决策，OpenAI Codex 不作为通用的 Provider Catalog Preset，而是定位为 ChatGPT 官方账号 the Responses Smoke / 反代边界。
当前，公开的 OpenAPI 声明 (`public-openapi.json`)、服务目录 (`provider-catalog.json`) 还没有将 Codex Responses 私有接口 (`/backend-api/codex/responses`) 的 Schema 以及专有接入和不支持特性配置完全严密闭环，这会导致客户端或外界对兼容范围产生误解。

### 目标
1. **OpenAPI 闭环**：在 `PublicDocsBundleService.java` 生成 of OpenAPI JSON 中显式追加 `/backend-api/codex/responses` 路径、HTTP POST 方法，以及完整的 Request / Response Schema 描述。
2. **Provider Catalog 严密收敛**：更新 `src/main/resources/provider-catalog.json`，确保 Codex 账号的专用属性（如 `unsupportedFeatures` 包含 Fine-tuning, Batches, Evals 等）配置完全，不再暗示支持非 Responses 官方能力。
3. **SDK 文档与接入示例指引**：更新 `docs/public-sdk-examples.md`，添加 Codex CLI 接入时的本地配置指南（如 `~/.codex/config.toml` 配置最佳实践，指引如何将通信协议配置为 `wire_api="responses"` 并指向网关）。

### 非目标
- 不实现非 Responses 的 Codex 内部 API（如 Fine-tuning / Admin 等）。
- 不声明 Codex 为通用 Provider Preset 选项。
- 暂时不运行真实连通性测试。

### 输入
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/main/resources/provider-catalog.json`
- `docs/public-sdk-examples.md`

### 输出
- 包含 Codex OpenAPI 规范的 `PublicDocsBundleService.java` 和生成的 `public-openapi.json`
- 包含 Codex 不支持特性的 `provider-catalog.json`
- 包含 `~/.codex/config.toml` 最佳配置的最佳指引 `docs/public-sdk-examples.md`

### 影响范围
- 网关 OpenAPI 生成服务、公共服务目录文件、客户端接入说明文档。

### 依赖
- `TASK-20260514-029-01` 事实源统一任务。

### 风险
- 频繁的 OpenAPI 大规模 Schema 手写容易出现语法或结构错误。我们必须严格通过 Gradlew 强类型编译校验。

### 验收标准
- `/public/docs/openapi.json` 返回的文档包含端点 `/backend-api/codex/responses` 描述，且其 Request Schema 具备 `messages`、`model`、`parent_message_id`、`reasoning_effort` 等专有字段。
- `provider-catalog.json` 的 Codex 配置里 `unsupportedFeatures` 列表准确包含了 `fine-tuning`, `batches`, `evals` 等非核心功能。
- `docs/public-sdk-examples.md` 存在关于 `~/.codex/config.toml` 的 CLI 代理说明。
- 编译无错误。

### 测试/验证边界
- 整体测试策略：本阶段遵照免跑测试策略，只进行强类型语法编译测试。
- 验证口径：使用 `.\gradlew.bat compileJava compileTestJava -x test`。

---

## 2. 关联文档与任务
- [事实源](../../docs/codex-functional-service-api-facts.md)
- [父任务](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)

---

## 3. 实现结果与验证
- **实现结果**：
  - `/backend-api/codex/responses` 私有端点的 OpenAPI 自动同步到快照中。
  - `provider-catalog.json` 中收缩 Codex 的配置，明确不支持 Fine-tuning, Batches 等，仅保留 Responses 边界。
  - 提供 `~/.codex/config.toml` 的本地代理配置指南。
- **验证情况**：
  - 后端编译 `BUILD SUCCESSFUL` 通过。
- **遗留问题**：无。
- **当前状态**：Completed (Done)

