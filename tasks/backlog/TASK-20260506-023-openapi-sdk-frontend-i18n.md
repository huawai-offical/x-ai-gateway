# TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取

状态：Backlog  
优先级：Medium  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-012 参考项目功能深度复核与任务再生成](../../docs/requirements/REQ-20260506-012-reference-depth-recheck-task-generation.md)

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

待处理。

## 测试/验证

待处理。

## 遗留问题

待处理。
