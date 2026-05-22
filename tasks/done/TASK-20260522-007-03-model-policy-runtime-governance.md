# TASK-20260522-007-03 Model Policy 第三阶段运行态治理

## 任务类型

子任务

## 背景

第三阶段要求把模型策略从静态映射推进到运行态治理，覆盖自动探测、健康裁剪、模型级限流额度、fallback chain 和灰度路由。

## 目标

- 自动探测模型列表时写入或更新策略元数据。
- 按 credential/account 健康、quota、runtime policy 裁剪候选。
- 支持模型级 rpm/tpm/request/token quota。
- 支持 fallback chain。
- 支持 canary/灰度权重。

## 非目标

- 不替代现有全局 DistributedKey 限流，只增加模型级补充限制。
- 不实现完整可视化运营看板。

## 上游来源

- `tasks/in-progress/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 输入

- `model_policy.runtime_policy_json`
- credential/account health 和 quota 字段。
- 现有 route candidate evaluation。

## 输出

- 候选裁剪 reason。
- fallback/canary 排序或权重。
- 模型级 quota/rate limit 判定结果。

## 影响范围

- Model Policy resolver/runtime evaluator。
- route selection candidate evaluation。
- credential model discovery service。

## 依赖

- 第一阶段 policy schema/resolver。
- 现有 health state、routing runtime enforcement、credential discovery。

## 风险

- 模型级限流和 key 级限流需要避免重复扣减。
- 灰度权重必须稳定且可解释。

## 验收标准

- [x] Discovery 可把发现模型转为策略元数据或默认 policy。
- [x] 健康/额度不足的 account/credential 被裁剪。
- [x] 模型级 rate/quota 生效。
- [x] fallback chain 决定候选顺序。
- [x] canary 权重影响同一 public model 的 upstream 候选排序。

## 测试边界

- resolver/evaluator 单元测试。
- route selection fallback/canary/rate quota 测试。

## 关联文档

- `docs/requirements/REQ-20260522-005-model-policy-layered-resolution.md`

## 关联任务

- 父任务：`tasks/in-progress/TASK-20260522-007-model-policy-layered-resolution-parent.md`

## 当前状态

Done

## 实施结果

- `CredentialModelDiscoveryService.refreshCredential` 已登记 Credential scope 的 `DISCOVERED` policy。
- `ModelPolicyResolver` 已按 account/credential quota、credential success rate、runtime rate limit 裁剪候选。
- `runtime_policy_json.fallbackChain` 会调整候选优先级。
- `runtime_policy_json.canary.weight` 会覆盖候选权重。

## 验证结果

- `ModelPolicyResolverTests.shouldApplyFallbackAndCanaryRuntimePolicyToCandidateWeightAndPriority`：通过。
- `ModelPolicyResolverTests.shouldRateLimitAfterRecordedSuccess`：通过。
