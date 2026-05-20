# TASK-20260518-006-03 Docs、catalog、conformance 与测试闭环

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260518-006](TASK-20260518-006-non-core-api-code-eradication.md)
上游来源：[REQ-20260518-006](../../docs/requirements/REQ-20260518-006-non-core-api-code-eradication.md)

## 背景

非核心 API 清理不能只停在 Java 代码。public docs、OpenAPI snapshot、provider catalog、conformance fixture 和任务文档如果继续保留旧口径，会在后续规划中把已删除能力重新带回。

## 目标

- 更新 public docs、OpenAPI、provider catalog、native compatibility 和 conformance fixture。
- 删除或改写非核心 controller/service tests。
- 回写 `REQ-20260518-005`、`REQ-20260518-006`、任务索引和父任务状态。

## 非目标

- 不做真实 provider smoke。
- 不重新设计 docs 生成体系。

## 输入

- `REQ-20260518-006`
- 清理后的代码事实源
- 定向测试结果

## 输出

- docs/tasks 回写。
- 残留搜索结果。
- 定向测试通过记录。

## 影响范围

- `docs/requirements`
- `docs/reports`
- `tasks`
- `src/test`
- `src/test/resources/conformance`
- `docs/openapi/public-openapi.json`
- `src/main/resources/provider-catalog.json`

## 依赖

- 类型、policy 与 async resource 清理完成。

## 风险

- 文档仍写“历史兼容层保留”会和用户要求冲突。

## 验收标准

- 关键残留符号搜索无匹配。
- public docs/OpenAPI/catalog/conformance/resource 定向测试通过。
- 父任务移动到 `tasks/done/`。

## 测试边界

- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`
- `NativeCompatibilityServiceTests`
- `EndpointConformanceMatrixTests`
- 残留 `rg` 搜索

## 当前状态

- 2026-05-19：已完成并由父任务统一验证；额外执行 `.\gradlew.bat test`，结果 `BUILD SUCCESSFUL`。
