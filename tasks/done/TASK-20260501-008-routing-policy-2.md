# TASK-20260501-008 路由策略 2.0：权重、自动 retry、fallback、熔断、用户/模型/账号限流可视化

状态：Done  
优先级：High  
来源：Linear X-289（已迁移到本地）  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联设计：[REQ-20260501-002](../../docs/requirements/REQ-20260501-002-priority-task-closure-design.md)

## 背景

`new-api` 支持渠道权重随机、失败自动重试、用户级模型限流；`Sub2API` 强调用户级和账号级并发限制、Token 速率限制、粘性会话；`CC Switch` 本地代理包含 failover、circuit breaker、health、provider router。当前 `x-ai-gateway` 已有路由、并发、亲和、SLO/Capacity 和 ErrorRule，但策略配置与可解释性仍可进一步统一。

## 本轮目标

在不破坏现有路由选择行为的前提下，增强路由候选解释性，为 route preview、Workbench 和后续策略配置打基础。

## 本轮完成范围

- 保持 `RouteCandidateEvaluation` response 结构不变。
- `scoreBreakdown` 新增：
  - `capability_score`
  - `priority_score`
  - `weight_score`
  - `affinity_bonus`
  - `cost_penalty`
  - `weighted_hash_jitter`
  - `total_score`
  - `retry_candidate`
  - `fallback_order`
- 分数组件由统一 `ScoreComponents` 生成，避免展示解释与真实排序漂移。

## 非目标

- 不引入不可解释的黑盒自动调度。
- 不破坏现有路由选择服务的兼容行为。
- 不在本轮完成数据库化 retry/fallback/circuit breaker 策略配置。

## 验收结果

- 路由候选可以解释权重、优先级、亲和、成本惩罚、hash 抖动与最终分数。
- 原有 cost guard、cooldown、governance 等拦截路径保持兼容。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"
```

关键覆盖：

- `shouldPreferPrefixAffinityWhenPresent` 新增 scoreBreakdown 断言。
- `shouldBlockRouteSelectionWhenCostGuardRejectsCandidate` 保持通过。

## 遗留问题

- retry/fallback/circuit breaker 的配置模型、Admin API 与前端可视化仍需后续任务承接。
