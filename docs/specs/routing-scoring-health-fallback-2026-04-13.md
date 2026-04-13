# X-22：候选评分、健康状态与首包前 fallback 事实来源（2026-04-13）

## 目标
- 将路由主链拆为“候选规划”和“执行尝试”两层。
- 明确评分、健康、冷却和 fallback 的执行边界。

## 候选规划
- 主入口：`GatewayRouteSelectionService.select`
- 固定顺序：
  1. `DistributedKey` 协议/模型/预算/并发治理
  2. 模型解析与 binding 合并
  3. provider 白名单过滤
  4. capability 真相源检查
  5. 生成 `RouteCandidateEvaluation`
  6. 动态健康检查、affinity 命中、得分计算
  7. 输出 `RouteSelectionResult`

## 硬过滤
- `provider_not_allowed`
- `feature_unsupported`
- `capability_deprioritized`
- `binding_inactive`
- `cooldown_active`
- `network_blocked`
- `account_pool_unavailable`

## 评分拆解
- `capability_rank`
- `binding_priority`
- `binding_weight`
- `health_state`
- `selection_source`

## 健康状态
- `HEALTHY`
- `COOLDOWN`
- `NETWORK_BLOCKED`
- `ACCOUNT_POOL_UNAVAILABLE`
- `FILTERED`

## 冷却与恢复
- 存储：`HealthStateStore`
- 默认 TTL：`5m`
- 触发场景：
  - sync/chat/resource 首包前异常
  - 429
  - 5xx
  - 空响应
- 成功后：
  - `recordSuccessfulSelection` 清理当前凭证 cooldown

## fallback 规则
### 同步聊天
- 在 `GatewayChatExecutionService.execute` 中按候选顺序尝试
- 首个候选失败且满足 fallback 条件时，切换到下一个候选
- 最终成功结果会回写最终选中候选与 attempt chain

### 流式聊天
- 在 `GatewayChatExecutionService.executeStream` 中递归尝试
- 只有在首个可见 chunk 发出前才能 fallback
- 一旦已有 text / reasoning / tool / terminal chunk 发出，后续错误只会失败，不再切换

### 资源执行
- 在 `GatewayResourceExecutionService` 中统一处理 JSON / binary / multipart 三类执行
- 429 / 5xx / 空 body 会触发下一候选尝试

## 观测与证据
- `RouteSelectionResult.candidateEvaluations`
- `RouteSelectionResult.attempts`
- `GatewayObservabilityService.recordRouteDecision`
- `RouteDecisionLogEntity.candidateSummaryJson`

## 关键代码锚点
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/routing/GatewayRouteSelectionService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/GatewayChatExecutionService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutionService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/observability/GatewayObservabilityService.java`

## 关键测试锚点
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/routing/GatewayRouteSelectionServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/GatewayChatExecutionServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutionServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/integration/GatewayEndToEndSmokeTests.java`
