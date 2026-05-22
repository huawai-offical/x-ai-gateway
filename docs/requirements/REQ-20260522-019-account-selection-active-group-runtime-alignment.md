# REQ-20260522-019 账号类选择 active 账号组运行时对齐

## 背景

`REQ-20260522-018` 已经把 API Key 凭证候选展开改为只认可“绑定 active 且账号组 active”的账号组绑定。但账号类运行时选择仍由 `AccountSelectionService` 直接读取 active 绑定行，再进入 sticky account 和账号候选选择；如果账号组被停用，已有 sticky account 或健康账号仍可能继续被选中。`ModelPolicyResolver` 在推导账号组上下文时也会读取 active 绑定行，需要同步排除停用账号组，避免策略上下文和运行时实际候选不一致。

## 目标

- `AccountSelectionService.hasHealthyAccountBinding` 排除停用账号组；存在绑定但全部账号组停用时返回不可用。
- `AccountSelectionService.resolveActiveAccount` 排除停用账号组，并阻止 sticky account 绕过账号组停用。
- sticky account 只在属于当前 active 账号组绑定且符合客户端家族限制时复用。
- `ModelPolicyResolver` 账号组上下文只包含 active 账号组绑定。
- 补充单元测试覆盖停用账号组、sticky 失效和健康检查口径。

## 范围

- `AccountSelectionService`
- `ModelPolicyResolver`
- `AccountSelectionServiceTests`
- 本地文档、任务和索引回写

## 非目标

- 不重写账号选择权重或负载均衡算法。
- 不新增账号组级 weight schema。
- 不改变无账号组绑定时 API Key 凭证 fallback 的现有语义。
- 不执行真实外部厂商 API 调用。

## 验收标准

- 只有停用账号组绑定时，账号选择返回空。
- 只有停用账号组绑定时，健康账号绑定检查返回 false。
- sticky account 不属于当前 active 账号组绑定时不会被复用。
- `ModelPolicyResolver` 不把停用账号组纳入账号组上下文。
- 后端定向测试和编译通过。

## 风险

- `hasHealthyAccountBinding` 历史上在没有任何绑定时返回 true，本轮保留该语义；只有“存在绑定但没有可用 active 账号组”时返回 false。
- sticky account 过滤更严格后，停用账号组会让旧 sticky key 自然失效，但不会主动删除 Redis key。

## 当前状态

Done

## 实现结果

- `AccountSelectionService.hasHealthyAccountBinding` 现在区分“没有任何账号组绑定”和“存在绑定但全部账号组停用”：前者保持历史 true 语义，后者返回 false。
- `AccountSelectionService.resolveActiveAccount` 只遍历启用账号组绑定，停用账号组不会再产生账号候选。
- sticky account 复用前会校验账号仍属于当前 active 账号组绑定、客户端家族仍允许、治理策略仍允许且网络健康。
- `ModelPolicyResolver` 的账号组上下文过滤停用账号组，策略匹配不再把停用组纳入 scope。
- 补充 `AccountSelectionServiceTests` 覆盖停用账号组、健康检查和 sticky 绕过防护。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.account.AccountSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.model.ModelPolicyResolverTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.DistributedKeyQueryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"`

## 遗留问题与后续建议

- Redis sticky key 不主动删除；停用账号组后会在选择时自然失效并写入新的可用账号。
- 本轮不新增账号组级 weight，也不改变账号选择排序算法。
- 本轮不执行真实外部厂商 API 调用。
