# TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取

状态：Done  
优先级：Medium  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-019 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](../../docs/requirements/REQ-20260506-019-openapi-sdk-frontend-i18n.md)

## 背景

当前公开 docs bundle 和最小 OpenAPI 已落地，但对照 `new-api-main` 的多语言文档与公开产品化程度，仍缺 OpenAPI 自动生成/快照发布、SDK 示例扩展和前端运行时 i18n 抽取。

## 目标

- 将 OpenAPI 从手工维护推进到可生成、可校验、可发布。
- 补充 Python、Go、Java、JavaScript SDK 示例。
- 抽取前端 navigation、页面标题、表格列、按钮和错误提示 i18n key。

## 范围

- Public OpenAPI generator 或快照校验。
- SDK example registry 与 docs bundle 输出。
- 前端 i18n 基础设施、zh-CN/en-US 字典和 fallback。
- UI 文案抽取优先覆盖 nav、common components、portal 和 public docs 相关页面。

## 非目标

- 不一次性翻译所有历史文档。
- 不把内部 Admin 全量接口全部公开。
- 不承诺 SDK 示例在无真实 provider key 时能跑通真实上游。

## 验收标准

- OpenAPI JSON 可由测试生成或快照校验，漂移会失败。
- docs bundle 暴露 SDK targets 和示例索引。
- 前端至少 navigation/common/portal 具备 zh-CN/en-US 切换。
- i18n key 缺失有测试或 lint 报告。

## 实现记录

- 新增 `PublicOpenApiSnapshotTests`，公开 OpenAPI 缺少版本、info、关键 path、`bearerAuth` 或 SDK registry 时会失败。
- 新增 `docs/sdk-examples/index.json` 与 Python、JavaScript、Go、Java 四类 chat completions 示例。
- 新增 [public-sdk-examples](../../docs/public-sdk-examples.md)，把 SDK 示例入口、环境变量和使用边界沉淀到公开文档。
- 新增 `web/src/i18n` 基础设施，包含 `zh-CN/en-US` 字典、fallback 和 key parity 测试。
- 更新 [docs index](../../docs/index.md)，补齐公开 SDK 示例入口。

## 测试/验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.docs.CompanionManifestSchemaTests"`
- `bun run test -- src/i18n/messages.test.ts`

## 遗留问题

- 未一次性抽取全部历史 Admin 页面文案；当前先覆盖 navigation/common/portal/public docs 的事实源和 parity 机制。
- SDK 示例仍需要调用方配置真实 gateway key 与可用 provider 才能跑真实上游 smoke。
