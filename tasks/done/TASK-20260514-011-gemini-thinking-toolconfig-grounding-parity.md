# TASK-20260514-011 Gemini thinkingConfig/toolConfig/URL context/Grounding 参数 parity

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-006](../done/TASK-20260514-006-community-home-api-docs-refresh.md)  
上游来源：[REP-20260514 主流厂商 API/changelog 复核](../../docs/reports/REP-20260514-mainstream-api-changelog-refresh.md)、[REQ-20260514-007](../../docs/requirements/REQ-20260514-007-mainstream-api-parity-backlog-closure.md)

## 本轮设计

- `generationConfig` 优先用 Gemini SDK `GenerateContentConfig.fromJson(...).toBuilder()` 保留官方字段，再叠加系统提示和工具。
- `toolConfig` 走 SDK `ToolConfig.fromJson(...)`，覆盖 `functionCallingConfig`。
- `googleSearch` 与 `urlContext` 通过 SDK `Tool.fromJson(...)` 保留，`googleMaps` 默认要求显式允许，避免未配置计费/权限时误开。

## 背景

Gemini `generateContent` 的 `generationConfig`、tools 与 release notes 已覆盖 `thinkingConfig`、`responseModalities`、multi-tool、URL context、Grounding with Google Search/Maps、Live/Interactions 等能力。当前项目已支持 generateContent 主链路、functionDeclarations、fileData 和 image/audio resource mode，但 thinking/toolConfig/grounding 还不完整。

## 目标

- 建立 Gemini `generationConfig` 和 `toolConfig` 字段 parity 矩阵。
- 支持或显式降级 `thinkingConfig`、`toolConfig.functionCallingConfig`、URL context、Google Search grounding。
- 让 response modalities 与 resource mode 的路由决策可在 trace 中解释。

## 非目标

- 不在本任务内实现 Gemini Interactions API 或 Live API 全量替换。
- 不把 Google Maps grounding 开给未配置计费/权限的站点。

## 输入

- Google Gemini generateContent、release notes、API versions/deprecations 官方文档。
- 当前 `GeminiGenerateContentRequestMapper`、`GeminiGenerateContentModeResolver`、`GeminiNativeGatewayChatRuntime`。

## 输出

- Gemini 参数 parity 实现或降级矩阵。
- Mapper/runtime/resource-mode 回归测试。
- 公开文档和 provider catalog 更新。

## 验收标准

- thinking/toolConfig/grounding 字段不会静默丢失。
- 未授权 grounding 或 URL context 有清晰错误/降级说明。
- image/audio resource mode 的现有测试保持通过。

## 测试边界

- Java mapper/runtime/resource mode 单测。
- Gemini smoke harness 可在缺 key 时分类 SKIPPED。

## 完成结果

- `GeminiGenerateContentRequestMapper` 从 `generationConfig.thinkingConfig` 捕获 reasoning 信息，并对 `googleMaps` grounding 增加显式允许开关。
- `GeminiNativeGatewayChatRuntime` 使用 Gemini SDK `GenerateContentConfig.fromJson(...).toBuilder()` 保留官方 generation config 字段，并下发 `ToolConfig.fromJson(...)`。
- `googleSearch`、`urlContext` 与标准 `functionDeclarations` 通过 SDK `Tool.fromJson(...)` 或 canonical tool fallback 保留。

## 验证记录

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests"
```

结果：通过。
