# TASK-20260514-028 OpenAI Administration API 权限隔离与只读优先

状态：Backlog  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

OpenAI Administration API 覆盖 organization、projects、users、service accounts、API keys、rate limits、usage、costs、audit logs、certificates 等企业管理能力。当前项目的 admin console 是自身管理后台，不等于 OpenAI Administration API。

## 目标

- 建立 Administration API 兼容矩阵。
- 默认只读优先：usage、costs、audit logs、projects/users 列表先行。
- 写操作如 project key、service account、certificate、rate limit 修改必须有独立权限、审计和确认。
- 区分平台管理员、租户管理员、普通用户可见范围。

## 非目标

- 不默认向所有客户暴露组织级 OpenAI Admin API。
- 不存储或展示未脱敏的上游 admin secret。

## 输入

- 官方 Administration API Reference。
- 本项目 admin auth、credential storage、audit log、billing rollup。

## 输出

- Administration API 权限模型。
- 只读 endpoint passthrough 或显式 out-of-scope。
- 写操作风险评估和审批流任务。

## 影响范围

- Admin security、credential resolver、audit persistence、billing/cost views、public docs。

## 依赖

- 具备 OpenAI organization admin 权限的测试账号。
- `TASK-20260514-031` secret vault 与 smoke 分类。

## 风险

- 权限过宽会泄露组织成本、用户或 API key 信息。
- 写操作可能破坏真实 OpenAI 组织配置。

## 验收标准

- Administration API 每个资源族有权限级别和支持状态。
- 只读接口默认脱敏，写接口默认关闭或需要显式开启。
- 所有 admin passthrough 都有审计事件。

## 测试边界

- Authorization tests。
- Audit tests。
- 真实 smoke 默认只读，写操作必须手动批准。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

