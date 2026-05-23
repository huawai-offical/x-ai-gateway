# TASK-20260523-012 凭证与厂商领域模型梳理父任务

状态：Done  
优先级：High  
上游来源：[REQ-20260523-011](../../docs/requirements/REQ-20260523-011-credential-provider-domain-model-clarification.md)

## 背景

用户反馈当前凭证体系复杂，并指出必须把账号分组纳入概念关系梳理。本任务只做本地文档和任务拆分，不进入代码实现。

## 目标

- 明确厂商、协议入口、账号分组、凭证之间的主关系。
- 明确 Provider Preset、Provider Site、Capability Snapshot、Model Policy、Distributed Key 的支撑层定位。
- 产出后续可实施任务。

## 非目标

- 不改数据库。
- 不改前端 UI。
- 不改运行时路由。

## 输出

- [REQ-20260523-011](../../docs/requirements/REQ-20260523-011-credential-provider-domain-model-clarification.md)
- [REP-20260523](../../docs/reports/REP-20260523-credential-provider-domain-model.md)
- 后续 backlog 子任务：
  - [TASK-20260523-013](../backlog/TASK-20260523-013-provider-catalog-vendor-endpoint-group-credential-ui.md)
  - [TASK-20260523-014](../backlog/TASK-20260523-014-provider-domain-api-naming-boundary.md)
  - [TASK-20260523-015](../backlog/TASK-20260523-015-capability-snapshot-refresh-semantics-redesign.md)
  - [TASK-20260523-016](../backlog/TASK-20260523-016-account-group-taxonomy-endpoint-coverage.md)

## 验收标准

- 关系模型清晰覆盖账号分组。
- 后续任务边界可执行、可验证。

## 验证记录

文档类任务，无代码测试。

## 完成记录

2026-05-23 已补充账号分组分类、当前实现映射和后续专门任务。当前结论是：账号分组在产品上必须作为一等对象；当前实现通过凭证同时绑定 `group_id` 与 `protocol_endpoint_id` 形成账号分组和协议入口交集，短期应由聚合 API 反推出分组覆盖范围，中期再评估显式绑定表。
