# TASK-20260522-007-01 Model Policy 策略表与运行时解析

## 任务类型

子任务

## 背景

父任务要求第一阶段先落地 `model_policy` 策略事实源，并接入运行时模型解析，确保后续管理端和第三阶段治理可以复用同一套解析结果。

## 目标

- 新增 `model_policy` 表、实体、repository。
- 实现 Model Policy resolver。
- 在 `GatewayRouteSelectionService` 中应用策略映射和候选裁剪。
- 空策略时保持旧路由行为。

## 非目标

- 不替换 `model_alias` 和 `site_model_capability`。
- 不实现前端页面。

## 上游来源

- `tasks/in-progress/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 输入

- 路由请求模型、协议、DistributedKey、候选 credentials。
- 各层 scope 的 allow/deny/mapping policy。

## 输出

- 有效 upstream model。
- 候选保留/排除原因。
- request/response override 聚合结果。

## 影响范围

- baseline schema
- persistence entity/repository
- `gateway/core/model`
- `GatewayRouteSelectionService`
- route result/plan 结构。

## 依赖

- `ModelCatalogQueryService`
- `DistributedKeyQueryService`
- `UpstreamCredentialRepository`
- `UpstreamAccountRepository`
- `UpstreamAccountGroupRepository`
- `UpstreamSiteProfileRepository`

## 风险

- policy scope 解析不完整可能导致 account 层策略不生效。
- 同一 public model 多映射需要保留 fallback 候选，不能只取第一条。

## 验收标准

- [x] schema、entity、repository 可编译。
- [x] resolver 支持 allow、deny、mapping、request/response override 存储和策略解释。
- [x] 路由候选能被 policy 排除并输出可解释 reason。
- [x] 空策略兼容旧行为。

## 测试边界

- `ModelPolicyResolverTests`
- `GatewayRouteSelectionServiceTests` 中新增策略映射与 deny 裁剪用例。

## 关联文档

- `docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`

## 关联任务

- 父任务：`tasks/in-progress/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 当前状态

Done

## 实施结果

- 已新增 `model_policy` 表、`ModelPolicyEntity`、`ModelPolicyRepository`。
- 已新增 `ModelPolicyResolver`，对当前 DistributedKey 可达 scope 执行安全匹配。
- 已接入 `GatewayRouteSelectionService`，策略开启时即时解析并按选中 candidate 设置 upstream model。

## 验证结果

- `ModelPolicyResolverTests`：通过。
- `GatewayRouteSelectionServiceTests.shouldRouteWithModelPolicyMappedUpstreamModel`：通过。
