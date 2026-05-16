# TASK-20260514-027 OpenAI Skills API 与工具分发

状态：Backlog  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

官方 API Reference 包含 Skills 资源。当前项目已有内部 tools/provider 概念，但没有 OpenAI Skills API 兼容入口。

## 目标

- 梳理 Skills API endpoint、对象模型和权限边界。
- 建立 Skills 与 Responses tools、MCP/custom tools 的关系。
- 支持技能列表、读取、创建/更新/删除或明确 out-of-scope。

## 非目标

- 不把 Codex 本地 skills 目录直接暴露为 OpenAI Skills API。
- 不执行未审核的第三方技能代码。

## 输入

- 官方 Skills API Reference。
- Responses tools、MCP/custom tools、plugin/deeplink 授权下发逻辑。

## 输出

- Skills compatibility matrix。
- Skills controller/service 或 out-of-scope 决策。
- 安全审核与租户隔离说明。

## 影响范围

- Tool registry、portal/admin capability display、security/audit、public docs。

## 依赖

- `TASK-20260514-019` Responses tool ecosystem。
- `TASK-20260514-030` 安全与错误模型。

## 风险

- 技能分发涉及执行边界和供应链风险。
- 与本地 Codex skills 概念混淆会误导用户。

## 验收标准

- Skills API 有明确 supported/partial/out-of-scope 状态。
- 与本地技能体系的差异写入文档。
- 如果实现入口，必须有权限和审计测试。

## 测试边界

- Compatibility matrix tests。
- Security negative tests。
- 无真实 key 时不做写入 smoke。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

