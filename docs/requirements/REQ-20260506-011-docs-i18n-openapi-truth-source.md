# REQ-20260506-011 文档、i18n 与 OpenAPI 事实源修复

状态：Done  
关联任务：[TASK-20260506-013 文档、i18n 与 OpenAPI 事实源修复](../../tasks/done/TASK-20260506-013-docs-i18n-openapi-truth-source.md)  
来源报告：[REP-20260506 三个参考项目功能完成度复核](../reports/REP-20260506-reference-feature-completeness-review.md)

## 背景

仓库已经采用本地 Markdown docs/tasks 作为默认事实源，但 `README.md` 仍残留“文档已统一迁移到 Notion”的旧表述。公开 docs bundle 已有 compatibility 内容，但 OpenAPI 入口、SDK 示例范围和 UI i18n 支持策略还没有成为可测试、可追踪的本地事实源。

## 目标

- README 明确指向 `docs/index.md`、`tasks/index.md` 和本地优先协作流程。
- `/public/docs/compatibility`、README、`docs/index.md` 三处事实一致。
- 新增 `/public/docs/openapi.json`，维护公开 API 的最小 OpenAPI JSON。
- docs bundle 返回 OpenAPI URL、SDK targets 和 i18n policy。
- 输出 UI i18n 缺口审计，明确 `zh-CN`/`en-US` 最低支持范围。

## 范围

- README、`docs/index.md`、`docs/public-api-compatibility.md`。
- `PublicDocsBundleService`、`PublicDocsBundleResponse`、`PublicDocsController`。
- `PublicDocsBundleServiceTests`。
- 新增 i18n/OpenAPI 审计报告。

## 非目标

- 不一次性翻译所有前端页面。
- 不引入完整 OpenAPI 生成器。
- 不承诺所有 SDK 示例能在无真实 provider 凭证时直接跑通。

## 验收标准

- README 不再指向线上 Notion 作为默认事实源。
- docs bundle 包含 OpenAPI URL、SDK targets、i18n policy。
- `/public/docs/openapi.json` 有单元测试覆盖基本路径。
- `docs/index.md` 收录本轮需求和审计报告。
- i18n 缺口清单可追踪。

## 实现结果

- README 已切回本地事实源入口。
- docs bundle 已包含 OpenAPI URL、SDK targets、i18n policy 和 Codex CLI 示例。
- `/public/docs/openapi.json` 已提供最小公开 OpenAPI JSON。
- `docs/openapi/public-openapi.json` 已落地本地维护版本。
- `docs/index.md` 已收录本轮需求、审计报告和 OpenAPI 文件。

## 验收结果

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
Get-Content -Path docs\openapi\public-openapi.json | ConvertFrom-Json | Select-Object -ExpandProperty openapi
```

结论：本地实现和测试通过，OpenAPI JSON 可解析为 `3.1.0`。前端运行时 i18n 切换和完整 OpenAPI 生成器作为后续增强。
