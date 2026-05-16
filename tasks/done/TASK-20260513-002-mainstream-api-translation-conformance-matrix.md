# TASK-20260513-002 主流 API 翻译 Conformance Matrix 与缺口硬化

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260513-001](../done/TASK-20260513-001-reference-translation-admin-portal-audit.md)  
上游来源：[REQ-20260513-002](../../docs/requirements/REQ-20260513-002-high-priority-gap-closure-implementation.md)、[REQ-20260513-001](../../docs/requirements/REQ-20260513-001-reference-translation-admin-portal-audit.md)、[REP-20260513](../../docs/reports/REP-20260513-reference-translation-admin-portal-audit.md)

## 背景

当前项目已实现 OpenAI、Anthropic、Gemini 主链路的 Canonical Translation 和 Native Runtime，但用户关心“无论使用 OpenAI API、Anthropic 还是任何主流 API，是否都能自动翻译”。该表述需要工程化收敛为可验证的支持矩阵，而不是口头承诺。

## 目标

- 建立主流 API 翻译 Conformance Matrix。
- 对 OpenAI Chat/Responses、Anthropic Messages、Gemini generateContent/streamGenerateContent 标记 `native`、`emulated`、`lossy`、`unsupported` 支持等级。
- 补齐或明确 Azure OpenAI、Vertex Gemini、xAI、Perplexity、OpenAI-compatible provider-specific 参数的支持边界。
- 硬化 tool schema、tool_choice、reasoning/thinking、vision、file、audio、streaming、usage、finish_reason、错误码映射。
- 将支持矩阵接入 Admin Native Compatibility 或 Workbench，可供后台查看和回归验证。

## 非目标

- 不承诺所有未知 API 自动无损互转。
- 不在本任务内新增所有 Provider 的专属计费价格同步。
- 不在本任务内实现桌面客户端能力。

## 输入

- `src/main/java/**/GatewayRequestFeatureService.java`
- `src/main/java/**/TranslationExecutionPlanCompiler.java`
- `src/main/java/**/OpenAi*RequestMapper.java`
- `src/main/java/**/Anthropic*RequestMapper.java`
- `src/main/java/**/Gemini*RequestMapper.java`
- `src/main/java/**/AnthropicNativeGatewayChatRuntime.java`
- `src/main/java/**/GeminiNativeGatewayChatRuntime.java`
- `web/src/features/workbench/`
- `web/src/features/provider-sites/`

## 输出

- Conformance Matrix 数据结构和展示入口。
- OpenAI/Anthropic/Gemini/provider-specific 缺口修复。
- 覆盖矩阵的自动化测试。
- 更新后的需求、报告或任务验收记录。

## 影响范围

- 网关协议转换。
- Native Runtime。
- Workbench/Native Compatibility/Provider Site 后台展示。
- provider catalog 支持等级描述。

## 依赖

- 现有 Translation Explain、Routing Preview、Execution Preview。
- 现有 provider catalog 和 provider site 模型。

## 风险

- 不同 Provider 参数差异较大，过度抽象可能导致语义丢失。
- 流式 tool/reasoning 事件补齐会影响 SSE 编码和前端展示。
- 支持矩阵必须准确，不能把 `lossy` 能力误标为 `native`。

## 验收标准

- 后台可以查看主流 API 翻译支持矩阵。
- 至少覆盖 OpenAI Chat、OpenAI Responses、Anthropic Messages、Gemini generateContent、Gemini streamGenerateContent、OpenAI-compatible。
- 对 unsupported/lossy 场景有明确提示。
- tool schema、tool_choice、reasoning/thinking、usage、finish_reason、error mapping 有回归测试或明确不支持记录。

## 测试边界

- 单测覆盖 mapper 与 runtime 参数映射。
- Controller 测试覆盖 explain/preview/support matrix。
- 至少一组端到端 smoke 验证 OpenAI/Anthropic/Gemini 三主链路。

## 实施结果

- 新增 `NativeTranslationConformanceRow`，在 `NativeCompatibilityResponse` 中返回 `translationConformance`。
- `NativeCompatibilityService` 现在同时提供 Native 路由矩阵和主流 API 翻译支持矩阵。
- Native 兼容前端页面新增“主流 API 翻译支持矩阵”，展示 `native`、`emulated`、`lossy`、`unsupported`。
- 支持矩阵明确 OpenAI、Responses、Anthropic、Gemini、OpenAI-compatible、Azure OpenAI、xAI/Perplexity/Vertex 的当前支持边界。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.TranslationExplainAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.TranslationExplainServiceTests"`
- `bun run typecheck`
- `bun run lint`
- Browser smoke：`/console/native-compatibility` 显示“主流 API 翻译支持矩阵”，包含 OpenAI 与 lossy 支持等级。

## 当前状态

Done。
