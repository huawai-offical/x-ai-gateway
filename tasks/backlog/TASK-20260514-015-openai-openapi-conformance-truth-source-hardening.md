# TASK-20260514-015 OpenAI 公开 OpenAPI、catalog 与 conformance 事实源校准

状态：Backlog  
优先级：Medium  
类型：子任务  
父任务：[REQ-20260514-008](../../docs/requirements/REQ-20260514-008-openai-api-compatibility-deep-audit.md)  
上游来源：[REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 背景

当前 `docs/openapi/public-openapi.json` 只公开 Chat/Responses/Web Search/Messages/Gemini/Media 等少数路径，但代码已经实现 Embeddings、Audio、Images、Files、Uploads、Batches、Fine-tuning、Moderations、Models、Realtime client secrets 等更多路径。与此同时 provider catalog 对 OpenAI Direct 的 unsupported features 为空，conformance accepted exceptions 又记录了未暴露的 list/events/checkpoints，三者不一致。

## 目标

- 让 public OpenAPI、provider catalog、public API compatibility docs、endpoint conformance matrix 使用同一个 OpenAI 兼容性事实口径。
- 把已实现但未公开的路径补到公开 OpenAPI，或明确标注为内部/未公开。
- 把未实现或部分实现的 OpenAI 官方资源族写入 `unsupportedFeatures`、accepted exceptions 或 backlog 链接。
- 为客户门户展示“OpenAI-compatible core”与“OpenAI official full API”差异。

## 非目标

- 不改变运行时代码行为。
- 不承诺缺失资源族在本任务内实现。

## 输入

- `docs/openapi/public-openapi.json`
- `docs/public-api-compatibility.md`
- `src/main/resources/provider-catalog.json`
- `src/test/resources/conformance/accepted-exceptions.json`
- `EndpointConformanceMatrixTests`

## 输出

- 更新后的公开 OpenAPI 路径列表。
- OpenAI provider catalog 支持/不支持字段校准。
- public docs 与 conformance accepted exceptions 一致。
- 面向客户的兼容等级说明。

## 影响范围

- `docs/openapi/public-openapi.json`
- `docs/public-api-compatibility.md`
- `src/main/resources/provider-catalog.json`
- `src/test/resources/conformance/accepted-exceptions.json`
- public docs bundle tests。

## 依赖

- `TASK-20260514-013` 和 `TASK-20260514-014` 的范围判断。
- OpenAI 官方 API Reference 最新资源清单。

## 风险

- 公开过多未稳定路径会增加兼容承诺。
- 公开过少会让客户看不到已实现能力，并让测试与销售口径失真。

## 验收标准

- public OpenAPI 与实际公开 controller 路径一致，差异有注释或文档说明。
- provider catalog 不再出现 OpenAI Direct `unsupportedFeatures: []` 与实际缺口冲突。
- conformance accepted exceptions 每一项都有 backlog、done task 或 out-of-scope 决策链接。

## 测试边界

- public docs bundle 单测。
- provider catalog loader 单测。
- conformance matrix 生成测试。
- 文档链接检索。

## 关联文档

- [REQ-20260514-008](../../docs/requirements/REQ-20260514-008-openai-api-compatibility-deep-audit.md)
- [REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 下游细分任务

- [TASK-20260514-029 OpenAI OpenAPI、Catalog、Conformance 与 SDK 事实源统一](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
- [TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护](TASK-20260514-031-openai-real-smoke-certification-harness.md)
