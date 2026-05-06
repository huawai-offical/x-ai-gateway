# TASK-20260506-013 文档、i18n 与 OpenAPI 事实源修复

状态：Done  
优先级：Medium  
来源：[REP-20260506 三个参考项目功能完成度复核](../../docs/reports/REP-20260506-reference-feature-completeness-review.md)
需求文档：[REQ-20260506-011 文档、i18n 与 OpenAPI 事实源修复](../../docs/requirements/REQ-20260506-011-docs-i18n-openapi-truth-source.md)

## 背景

对照 `new-api-main` 与 `cc-switch-main`，当前 `x-ai-gateway` 的公开文档、README、i18n 和 OpenAPI/SDK 示例仍不完整。尤其 `README.md` 仍残留“文档已统一迁移到 Notion”的旧表述，与本仓库本地优先事实源冲突。

## 目标

- 修复本地文档事实源不一致。
- 建立公开 API/兼容矩阵/OpenAPI/SDK 示例的可生成或可校验链路。
- 明确 UI i18n 的最低支持范围。

## 范围

- 更新 README，指向 `docs/index.md` 与本地 docs/tasks 流程。
- 扩展 `/public/docs/compatibility` 内容，覆盖新 provider/feature matrix。
- 生成或维护 OpenAPI JSON，并补 curl、OpenAI SDK、Claude/Gemini/Codex CLI 示例。
- UI i18n key 审计，至少明确 zh-CN/en-US 支持策略。

## 非目标

- 不一次性翻译所有历史文档。
- 不承诺所有 provider SDK 示例均能真实跑通。

## 验收标准

- README 不再指向线上 Notion 作为默认事实源。
- 公开 docs bundle、README、docs/index 三处事实一致。
- OpenAPI/compatibility 文档有测试或快照校验。
- i18n 缺口清单可追踪。

## 详细设计

- README 改为本地 docs/tasks 入口，并列出公开 docs API。
- `PublicDocsBundleResponse` 增加 `openApiUrl`、`openApiSpecVersion`、`sdkTargets`、`i18nPolicy`。
- `PublicDocsBundleService` 增加最小公开 OpenAPI JSON，`PublicDocsController` 暴露 `/public/docs/openapi.json`。
- 新增 i18n/OpenAPI 审计报告并收录到 `docs/index.md`。

## 进度记录

- 2026-05-06：任务从 backlog 移入 in-progress，补充本地需求、审计报告与详细设计，开始修复事实源。
- 2026-05-06：完成 README 本地事实源修复、public docs bundle OpenAPI/i18n 字段、OpenAPI JSON endpoint、本地 OpenAPI 文件、i18n 审计报告和回归测试。

## 实现结果

- `README.md` 改为指向 `docs/index.md`、`tasks/index.md` 和本地协作流程，不再保留 Notion URL。
- `PublicDocsBundleResponse` 增加 `openApiUrl`、`openApiSpecVersion`、`sdkTargets`、`i18nPolicy`。
- `PublicDocsBundleService#openApi` 和 `GET /public/docs/openapi.json` 提供最小公开 OpenAPI JSON。
- `docs/openapi/public-openapi.json` 落地本地维护版本。
- `docs/public-api-compatibility.md` 补充 OpenAPI、Codex CLI 和 i18n 策略。
- `docs/reports/REP-20260506-i18n-openapi-truth-source-audit.md` 记录 i18n/OpenAPI 缺口清单。
- `docs/index.md` 收录本轮需求、报告和 OpenAPI 文件。

## 验证情况

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
Select-String -Path README.md -Pattern 'Notion|notion.so|Linear'
Get-Content -Path docs\openapi\public-openapi.json | ConvertFrom-Json | Select-Object -ExpandProperty openapi
```

覆盖点：

- docs bundle 新增 OpenAPI URL、SDK targets、i18n policy 和 Codex CLI 示例。
- OpenAPI JSON 包含 docs、OpenAI-compatible、Media provider matrix 和 bearer auth。
- README 不再包含线上 Notion URL。
- 本地 OpenAPI JSON 可解析为 `3.1.0`。

## 遗留问题

- 前端运行时语言切换仍未启用。
- 完整 OpenAPI 生成器和全量 Admin API spec 未纳入本轮。

## 后续建议

- 后续优先抽取前端 navigation、标题、表格列名、按钮和错误提示的 i18n key。
- 将 OpenAPI JSON 接入快照发布或生成器，减少手工维护漂移。
