# TASK-20260524-001-05 文档、OpenAPI 与 Smoke 范围对齐

状态：In Progress  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-001](TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

支持范围从“功能性服务 API + 多 provider 广度”进一步收敛为“头部自有模型厂商 native + 无损翻译”。文档、OpenAPI、SDK 示例和 smoke harness 必须同步，否则会继续给客户端造成模糊成功预期。

## 目标

- 更新 docs/index、public API compatibility、functional service matrix、OpenAPI snapshot。
- 更新 SDK 示例，强调 OpenAI/Anthropic/Gemini/MiMo/DeepSeek/xAI 核心厂商。
- 更新 smoke harness 范围，移除 Dify 等非核心 provider。
- 将不可对应能力失败策略写入错误码与兼容说明。

## 非目标

- 不恢复官方 API 全量覆盖文档。
- 不为清理掉的 provider 维护官方级 smoke。
- 不引入线上 Notion/Linear。

## 输入

- 核心厂商目录清单。
- native adapter contract。
- 无损翻译矩阵。
- 不可对应能力失败清单。

## 输出

- 更新后的公开文档和 OpenAPI。
- smoke harness 范围说明。
- SDK 示例范围调整。

## 影响范围

- `docs/index.md`
- `docs/public-api-compatibility.md`
- `docs/functional-service-api-coverage-matrix.md`
- `docs/openapi/public-openapi.json`
- `docs/public-sdk-examples.md`
- `docs/testing-smoke-harness.md`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/docs/PublicOpenApiSnapshotTests.java`

## 依赖

- 前四个子任务输出。

## 风险

- 文档先于实现会造成误导，必须标明 Draft / Backlog 状态。
- OpenAPI snapshot 与运行时代码不一致会破坏测试。

## 验收标准

- 文档和 OpenAPI 不再宣称 local fake 能力成功。
- 核心厂商范围一致。
- smoke harness 只对核心厂商承诺官方级验证。

## 测试边界

- docs bundle tests。
- public OpenAPI snapshot tests。
- provider smoke fixture tests。

## 当前状态

- 2026-05-24：进入实施。第一阶段先收紧 public docs bundle、`docs/public-api-compatibility.md` 与 OpenAPI snapshot，明确默认核心 provider 清单、Lossless Translation Matrix 失败优先规则和 native-required 错误码。
- 2026-05-24：已完成 public docs/OpenAPI 第一阶段对齐：Dify、OpenRouter、Together、Fireworks、SiliconFlow 与 generic OpenAI-compatible 不再出现在默认核心兼容承诺；公开错误码新增 `unsupported_translation_attribute`、`native_route_required`、`native_compaction_required`；OpenAPI 对 `/v1/audio/translations`、`/v1/images/edits`、`/v1/images/variations` 明确 native-required 失败码。
- 2026-05-24：已通过 `PublicDocsBundleServiceTests` 与 `PublicOpenApiSnapshotTests`。剩余范围为 SDK 示例、functional service matrix 和 smoke harness 的核心 provider 范围继续收敛。
