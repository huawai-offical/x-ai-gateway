# REQ-20260506-019 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](../../tasks/done/TASK-20260506-023-openapi-sdk-frontend-i18n.md)

## 背景

当前仓库已有 `docs/openapi/public-openapi.json` 和部分公开文档，但 OpenAPI 仍偏静态快照，SDK 示例和前端 i18n 事实源也不完整。为避免接口、文档和前端文案长期漂移，需要建立可校验的 OpenAPI 快照、SDK 示例索引和前端 i18n 抽取清单。

## 目标

- 建立 OpenAPI 快照校验与发布清单，漂移时能通过测试暴露。
- 补齐 Python、Go、Java、JavaScript SDK 示例索引。
- 建立前端 i18n 字典、fallback 和缺失 key 检查。
- 优先覆盖 navigation、common、portal/public docs 相关文案。

## 范围

- `docs/openapi/public-openapi.json` 校验与摘要生成。
- SDK 示例 registry、示例文件和 docs bundle 链接。
- `web/src/i18n` 基础设施和 `zh-CN/en-US` 字典。
- i18n key parity 测试或脚本。

## 非目标

- 不一次性翻译所有历史页面。
- 不把内部 Admin 全量接口都公开。
- SDK 示例不承诺在无真实 provider key 时跑通真实上游。

## 验收标准

- OpenAPI 快照可被测试校验，缺少关键字段会失败。
- SDK 示例索引覆盖 Python、Go、Java、JavaScript。
- 前端 i18n 字典具备 zh-CN/en-US key parity 检查。
- 文档、任务状态和验证结果完成回写。

## 实现结果

- 新增 `PublicOpenApiSnapshotTests`，把公开 OpenAPI 快照纳入测试约束，覆盖 OpenAPI 版本、info、公开 path、`bearerAuth` 和 SDK registry 链接。
- 新增 [public-sdk-examples](../public-sdk-examples.md) 与 `docs/sdk-examples/index.json`，沉淀 Python、JavaScript、Go、Java 四类示例入口。
- 新增 `docs/sdk-examples/python/chat_completions.py`、`docs/sdk-examples/javascript/chat-completions.mjs`、`docs/sdk-examples/go/chat_completions.go`、`docs/sdk-examples/java/ChatCompletionsExample.java`。
- 新增 `web/src/i18n/messages.ts`、`web/src/i18n/index.ts` 和 `web/src/i18n/messages.test.ts`，覆盖 `zh-CN/en-US` 字典、fallback 和 key parity 检查。
- 更新 [docs index](../index.md)，将公开 SDK 示例纳入文档索引。

## 测试/验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.docs.CompanionManifestSchemaTests"`
- `bun run test -- src/i18n/messages.test.ts`

## 遗留问题

- 本轮先完成公开 OpenAPI 快照校验、SDK 示例和 i18n 基础设施；未一次性抽取所有历史 Admin 页面文案。
- SDK 示例使用标准 OpenAI-compatible 调用形态，真实 provider smoke 仍依赖用户提供的可用 provider key 和额度。
